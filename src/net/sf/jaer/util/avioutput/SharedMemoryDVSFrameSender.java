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
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.chip.AEChip;

/**
 * Publishes accumulated DVS event-count frames to a memory-mapped file for a
 * local Python consumer, with a localhost TCP JSON-lines control channel.
 * <p>
 * Pixel path: two slots of {@code 64-byte header + uint8[height*width]}, little
 * endian. Magic bytes {@code JAER}, version 1, {@code channels=1},
 * {@code dtype=U8}. Pixels are rectified event counts clipped at
 * {@code dvsGrayScale} and scaled to 0–255 (same as dextra-roshambo-python
 * {@code producer.py}), not the 3-sigma display pixmap.
 * <p>
 * Control path: TCP {@code 127.0.0.1:controlPort} (default 14100). On connect,
 * a {@code HELLO} JSON line; on each filled frame, {@code FRAME_READY}. Python
 * can also poll slot headers if TCP is unused.
 * <p>
 * Hello world (dextra-roshambo-python):
 * {@code python consumer.py --jaer-mmap <mmapPath> --serial_port None}
 *
 * @author tobi
 */
@Description("Publishes DVS event-count frames to a mmap file + localhost TCP for a Python consumer (e.g. dextra-roshambo-python)")
@Help("""
<html>
<body>
<h2>SharedMemoryDVSFrameSender</h2>
<p>Publishes 64&times;64 DVS <b>event-count</b> frames to a memory-mapped file so a local Python process
can classify them (for example
<a href="https://github.com/SensorsINI/dextra-roshambo-python">dextra-roshambo-python</a>).
This replaces <code>producer.py</code> / pyaer for a jAER hello-world.</p>
<p>See also the
<a href="https://sensors.ini.ch/research/projects/dextra">Dextra project</a>
and <a href="https://github.com/SensorsINI/jaer">jAER</a>.</p>
<hr>
<h3>1. Enable this filter</h3>
<ol>
<li>Check <b>Enabled</b> on this panel (it is on the default filter list).</li>
<li>Leave defaults: output size <b>64&times;64</b>, <code>dvsGrayScale=16</code>,
<code>rectifyPolarities=true</code>, <code>normalizeFrame=false</code>, <code>showFrames=true</code>.</li>
<li>Note <code>mmapPath</code> (currently <code>{mmapPath}</code>) and
<code>controlPort</code> (currently <code>{controlPort}</code>).</li>
</ol>
<p>Linux/macOS typical path: <code>/tmp/jaer_dvs_frames.mmap</code>.
Windows: <code>%TEMP%\\jaer_dvs_frames.mmap</code> (same as this filter's default).</p>
<h3>2. Play events</h3>
<p>Start a live camera, or open sample rock/scissors/paper throws:
<a href="https://drive.google.com/file/d/1hEI4HMODwAu6Pm9P4oDecePbfv--Lwbg/view?usp=drive_link">Davis346 Roshambo throws</a>
(<code>Davis346mini-2017-12-19T16-04-50+0100-00000001-0 roshambo samples tobi.aedat</code>,
part of <a href="https://sites.google.com/view/davis24-davis-sample-data/home">DAVIS24</a>).
Play with chip <b>Davis346blue</b>; drag the AEDAT onto AEViewer.
The ImageDisplay (if <code>showFrames</code> is on) should update; those pixels are the
same uint8 counts written to the mmap file (clipped at <code>dvsGrayScale</code>, scaled 0&ndash;255).</p>
<h3>3. Run the Python consumer</h3>
<p>In <code>dextra-roshambo-python</code>, with the TensorFlow 2.5 / Python 3.9 environment.
Use <code>--windowed</code> for a 640&times;640 inference window instead of fullscreen:</p>
<pre>
python consumer.py --jaer-mmap {mmapPath} --serial_port None --windowed
</pre>
<p><code>--jaer-tcp 127.0.0.1:{controlPort}</code> is the default with <code>--jaer-mmap</code>.
Use <code>--jaer-tcp None</code> to poll mmap sequence numbers only (no TCP).</p>
<hr>
<h3>If the CNN image looks upside-down</h3>
<p>Toggle <code>flipY</code> so row 0 matches the training set (OpenCV / top-left origin).</p>
<h3>Protocol (v1)</h3>
<p>Frames are shared through a <b>double-buffered memory-mapped file</b>: two slots of header plus
uint8 pixels, so the producer can write one slot while the consumer reads the other.
A localhost <b>TCP control channel</b> (JSON lines on <code>127.0.0.1</code>) synchronizes producer and consumer
and tells the consumer the file path, size, and layout: on connect it sends <code>HELLO</code>;
on each filled frame it sends <code>FRAME_READY</code>. If TCP is unused, the consumer can poll slot
headers for a new sequence number.</p>
<p>Each slot is little-endian. Slot size = 64 + width&times;height bytes. Magic <code>JAER</code>,
<code>channels=1</code>, <code>dtype=U8</code>.</p>
<p>Source:
<a href="https://github.com/SensorsINI/jaer/blob/master/src/net/sf/jaer/util/avioutput/SharedMemoryDVSFrameSender.java">SharedMemoryDVSFrameSender.java</a>.</p>
<p>jAER does not load TensorFlow; CNN weights stay in the Python project.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SharedMemoryDVSFrameSender extends DvsFramerSingleFrame {

    public static final int PROTOCOL_VERSION = 1;
    public static final int HEADER_BYTES = 64;
    public static final int NUM_BUFFERS = 2;
    public static final int DTYPE_U8 = 1;
    public static final int DEFAULT_CONTROL_PORT = 14100;
    public static final byte[] MAGIC = new byte[]{'J', 'A', 'E', 'R'};

    private String mmapPath;
    private int controlPort = getInt("controlPort", DEFAULT_CONTROL_PORT);
    private boolean flipY = getBoolean("flipY", false);
    private boolean deleteMmapOnCleanup = getBoolean("deleteMmapOnCleanup", true);

    private RandomAccessFile mmapFile;
    private FileChannel mmapChannel;
    private MappedByteBuffer mapped;
    private int mappedWidth;
    private int mappedHeight;
    private int nextSeq = 1;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private Thread notifyThread;
    private final CopyOnWriteArrayList<Socket> clients = new CopyOnWriteArrayList<>();
    private final AtomicBoolean publisherRunning = new AtomicBoolean(false);
    private final AtomicReference<String> pendingNotify = new AtomicReference<>();
    private final Object notifyLock = new Object();

    public SharedMemoryDVSFrameSender(AEChip chip) {
        super(chip);
        mmapPath = getString("mmapPath", defaultMmapPath());
        setDvsGrayScale(getInt("dvsGrayScale", 16));
        setNormalizeFrame(getBoolean("normalizeFrame", false));
        setShowFrames(getBoolean("showFrames", true));
        setPropertyTooltip("mmapPath", "memory-mapped file path shared with the Python consumer (cross-platform file mmap)");
        setPropertyTooltip("controlPort", "localhost TCP port for JSON-lines HELLO / FRAME_READY (bind 127.0.0.1 only)");
        setPropertyTooltip("flipY", "if true, write row 0 as sensor y=height-1 (OpenCV / top-left origin)");
        setPropertyTooltip("deleteMmapOnCleanup", "delete the mmap file when the filter is disabled or jAER exits");
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
        return new File(System.getProperty("java.io.tmpdir"), "jaer_dvs_frames.mmap").getAbsolutePath();
    }

    public static int slotSize(int width, int height) {
        return HEADER_BYTES + Math.max(0, width) * Math.max(0, height);
    }

    public static int fileSize(int width, int height) {
        return NUM_BUFFERS * slotSize(width, height);
    }

    @Override
    public void initFilter() {
        super.initFilter();
        if (isFilterEnabled()) {
            startPublisher();
        }
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
        if (publisherRunning.get()) {
            remap(width, getOutputImageHeight());
        }
    }

    @Override
    synchronized public void setOutputImageHeight(int height) {
        super.setOutputImageHeight(height);
        if (publisherRunning.get()) {
            remap(getOutputImageWidth(), height);
        }
    }

    @Override
    protected void processDvsFrame(DvsFrame frame) {
        publishFrame(frame);
    }

    synchronized void startPublisher() {
        if (publisherRunning.get()) {
            return;
        }
        try {
            remap(getOutputImageWidth(), getOutputImageHeight());
            startControlServer();
            publisherRunning.set(true);
            log.info(String.format("SharedMemoryDVSFrameSender mmap=%s  TCP=127.0.0.1:%d  %dx%d",
                    mmapPath, controlPort, getOutputImageWidth(), getOutputImageHeight()));
        } catch (IOException e) {
            log.warning("could not start shared-memory publisher: " + e);
            stopPublisher();
        }
    }

    synchronized void stopPublisher() {
        publisherRunning.set(false);
        stopControlServer();
        unmap(deleteMmapOnCleanup);
    }

    private void remap(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (mapped != null && mappedWidth == width && mappedHeight == height) {
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
            long size = fileSize(width, height);
            mmapFile.setLength(size);
            mmapChannel = mmapFile.getChannel();
            mapped = mmapChannel.map(FileChannel.MapMode.READ_WRITE, 0, size);
            mapped.order(ByteOrder.LITTLE_ENDIAN);
            mappedWidth = width;
            mappedHeight = height;
            writeEmptyHeaders();
            log.info(String.format("mapped %s (%d bytes, 2 x %dx%d uint8)", mmapPath, size, width, height));
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
            writeHeader(slot, 0, mappedWidth, mappedHeight, 0);
        }
        mapped.force();
    }

    private void unmap(boolean deleteFile) {
        mapped = null;
        mappedWidth = 0;
        mappedHeight = 0;
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

    private void publishFrame(DvsFrame frame) {
        if (!publisherRunning.get()) {
            return;
        }
        int width = frame.getWidth();
        int height = frame.getHeight();
        int[] eventSum = frame.getEventSum();
        if (eventSum == null || width <= 0 || height <= 0) {
            return;
        }
        if (mapped == null || mappedWidth != width || mappedHeight != height) {
            remap(width, height);
        }
        if (mapped == null) {
            return;
        }
        int slot = (nextSeq & 1);
        int seq = nextSeq++;
        int gray = Math.max(1, getDvsGrayScale());
        int n = width * height;
        int pixelBase = slot * slotSize(width, height) + HEADER_BYTES;
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
        writeHeader(slot, seq, width, height, frame.getLastTimestampUs() & 0xffffffffL);
        mapped.force();
        String json = String.format(
                "{\"type\":\"FRAME_READY\",\"seq\":%d,\"width\":%d,\"height\":%d,\"channels\":1,\"dtype\":\"U8\",\"buffer_index\":%d,\"timestamp_us\":%d,\"stride_bytes\":%d}",
                seq, width, height, slot, frame.getLastTimestampUs() & 0xffffffffL, width);
        pendingNotify.set(json);
        synchronized (notifyLock) {
            notifyLock.notifyAll();
        }
    }

    private void writeHeader(int slot, int seq, int width, int height, long timestampUs) {
        int base = slot * slotSize(width, height);
        mapped.order(ByteOrder.LITTLE_ENDIAN);
        mapped.put(base, MAGIC[0]);
        mapped.put(base + 1, MAGIC[1]);
        mapped.put(base + 2, MAGIC[2]);
        mapped.put(base + 3, MAGIC[3]);
        mapped.putShort(base + 4, (short) PROTOCOL_VERSION);
        mapped.putShort(base + 6, (short) 0); // flags
        mapped.putInt(base + 12, width);
        mapped.putInt(base + 16, height);
        mapped.putInt(base + 20, width); // stride_bytes for uint8 grayscale
        mapped.putShort(base + 24, (short) 1); // channels
        mapped.putShort(base + 26, (short) DTYPE_U8);
        mapped.putLong(base + 28, timestampUs);
        mapped.putInt(base + 8, seq); // seq last: publication fence after pixels + rest of header
    }

    private void startControlServer() throws IOException {
        stopControlServer();
        serverSocket = new ServerSocket(controlPort, 8, InetAddress.getByName("127.0.0.1"));
        acceptThread = new Thread(this::acceptLoop, "SharedMemoryDVSFrameSender-accept");
        acceptThread.setDaemon(true);
        notifyThread = new Thread(this::notifyLoop, "SharedMemoryDVSFrameSender-notify");
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
                "{\"type\":\"HELLO\",\"version\":%d,\"path\":\"%s\",\"width\":%d,\"height\":%d,\"header_bytes\":%d,\"num_buffers\":%d,\"channels\":1,\"dtype\":\"U8\"}",
                PROTOCOL_VERSION, jsonEscape(mmapPath), getOutputImageWidth(), getOutputImageHeight(), HEADER_BYTES, NUM_BUFFERS);
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
            remap(getOutputImageWidth(), getOutputImageHeight());
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
