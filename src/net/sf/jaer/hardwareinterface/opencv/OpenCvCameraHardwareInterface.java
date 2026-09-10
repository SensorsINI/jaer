package net.sf.jaer.hardwareinterface.opencv;

import java.beans.PropertyChangeSupport;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketBundlePool;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.OpenCVNativeLoader;

/**
 * OpenCV {@code VideoCapture} as a typed {@link FramePacket} source.
 */
public class OpenCvCameraHardwareInterface implements AEMonitorInterface {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Set<Integer> OPEN_INDICES = new CopyOnWriteArraySet<>();

    private final int cameraIndex;
    private final int api;
    private final String label;
    private final int probeWidth;
    private final int probeHeight;

    private final PacketBundlePool pool = new PacketBundlePool();
    private final AEPacketRaw emptyRaw = new AEPacketRaw(0);
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicBoolean acquire = new AtomicBoolean(false);
    private final AtomicLong originNanos = new AtomicLong(System.nanoTime());
    private final AtomicBoolean overrun = new AtomicBoolean(false);

    private volatile AEChip chip;
    private volatile VideoCapture capture;
    private volatile Thread captureThread;
    private volatile PacketBundle lastBundle = new PacketBundle();
    private int lastNumEvents;
    private int estimatedRate;
    private int aeBufferSize = 1;

    public OpenCvCameraHardwareInterface(int cameraIndex, int api, String label, int width, int height) {
        this.cameraIndex = cameraIndex;
        this.api = api;
        this.label = label == null ? ("OpenCV: " + cameraIndex) : label;
        this.probeWidth = width;
        this.probeHeight = height;
    }

    public int getCameraIndex() {
        return cameraIndex;
    }

    public static boolean isIndexOpen(int index) {
        return OPEN_INDICES.contains(index);
    }

    public static boolean sameDevice(HardwareInterface a, HardwareInterface b) {
        if (!(a instanceof OpenCvCameraHardwareInterface) || !(b instanceof OpenCvCameraHardwareInterface)) {
            return false;
        }
        return ((OpenCvCameraHardwareInterface) a).cameraIndex
                == ((OpenCvCameraHardwareInterface) b).cameraIndex;
    }

    @Override
    public String getTypeName() {
        return "OpenCV";
    }

    @Override
    public String toString() {
        return label;
    }

    @Override
    public synchronized void open() throws HardwareInterfaceException {
        if (open.get()) {
            return;
        }
        if (!OpenCVNativeLoader.load()) {
            throw new HardwareInterfaceException("OpenCV native library is not available");
        }
        if (OPEN_INDICES.contains(cameraIndex)) {
            throw new HardwareInterfaceException("OpenCV camera " + cameraIndex + " is already open");
        }
        VideoCapture cap = new VideoCapture();
        if (!cap.open(cameraIndex, api) && !cap.open(cameraIndex, Videoio.CAP_ANY)) {
            cap.release();
            throw new HardwareInterfaceException("Could not open OpenCV camera index " + cameraIndex);
        }
        capture = cap;
        OPEN_INDICES.add(cameraIndex);
        originNanos.set(System.nanoTime());
        open.set(true);
        applyChipGeometryFromCapture(cap);
        setEventAcquisitionEnabled(true);
        log.info("Opened " + label);
    }

    private void applyChipGeometryFromCapture(VideoCapture cap) {
        int w = (int) cap.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int h = (int) cap.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        if (w <= 0) {
            w = probeWidth;
        }
        if (h <= 0) {
            h = probeHeight;
        }
        applyChipGeometry(w, h);
    }

    void applyChipGeometry(int w, int h) {
        AEChip c = chip;
        if (c == null || w <= 0 || h <= 0) {
            return;
        }
        if (c.getSizeX() != w || c.getSizeY() != h) {
            c.setSizeX(w);
            c.setSizeY(h);
        }
    }

    @Override
    public synchronized void close() {
        acquire.set(false);
        Thread t = captureThread;
        captureThread = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        VideoCapture cap = capture;
        capture = null;
        if (cap != null) {
            try {
                cap.release();
            } catch (Exception e) {
                log.log(Level.FINE, "OpenCV close: " + e, e);
            }
        }
        OPEN_INDICES.remove(cameraIndex);
        open.set(false);
        pool.reset();
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        lastNumEvents = 0;
        overrun.set(false);
        return emptyRaw;
    }

    @Override
    public PacketBundle acquireAvailablePacketBundle() throws HardwareInterfaceException {
        if (!open.get()) {
            open();
        }
        if (!acquire.get()) {
            setEventAcquisitionEnabled(true);
        }
        synchronized (pool) {
            pool.swap();
            lastBundle = pool.readBuffer();
        }
        lastNumEvents = lastBundle == null || lastBundle.isEmpty() ? 0 : 1;
        overrun.set(false);
        return lastBundle;
    }

    @Override
    public int getNumEventsAcquired() {
        return lastNumEvents;
    }

    @Override
    public AEPacketRaw getEvents() {
        return emptyRaw;
    }

    @Override
    public void resetTimestamps() {
        originNanos.set(System.nanoTime());
    }

    @Override
    public boolean overrunOccurred() {
        return overrun.getAndSet(false);
    }

    @Override
    public int getAEBufferSize() {
        return aeBufferSize;
    }

    @Override
    public void setAEBufferSize(int AEBufferSize) {
        this.aeBufferSize = Math.max(1, AEBufferSize);
    }

    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        acquire.set(enable);
        if (enable) {
            startCaptureThread();
        }
    }

    @Override
    public boolean isEventAcquisitionEnabled() {
        return acquire.get();
    }

    private synchronized void startCaptureThread() {
        if (captureThread != null && captureThread.isAlive()) {
            return;
        }
        Thread t = new Thread(this::captureLoop, "jaer-opencv-capture-" + cameraIndex);
        t.setDaemon(true);
        captureThread = t;
        t.start();
    }

    private void captureLoop() {
        Mat mat = new Mat();
        FramePacket frame = new FramePacket();
        while (acquire.get() && open.get()) {
            VideoCapture cap = capture;
            if (cap == null || !cap.isOpened()) {
                break;
            }
            if (!cap.read(mat) || mat.empty()) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            int w = mat.cols();
            int h = mat.rows();
            int ch = mat.channels();
            applyChipGeometry(w, h);
            copyMatToFrame(mat, w, h, ch, frame);
            long us = timestampUs();
            frame.setTimestampStartUs(us);
            frame.setTimestampEndUs(us);
            frame.setExposureUs(0);
            synchronized (pool) {
                PacketBundle wb = pool.writeBuffer();
                if (!wb.isEmpty()) {
                    overrun.set(true);
                    wb.clear();
                }
                FramePacket copy = copyFrame(frame);
                wb.add(copy);
            }
            estimatedRate = 30;
        }
        mat.release();
    }

    private long timestampUs() {
        long dt = System.nanoTime() - originNanos.get();
        if (dt < 0) {
            dt = 0;
        }
        return dt / 1000L;
    }

    /**
     * OpenCV BGR, y=0 at top → jAER RGB {@link FramePacket}, y=0 at bottom.
     */
    static void copyMatToFrame(Mat mat, int w, int h, int ch, FramePacket frame) {
        FramePacket.ColorMode mode = ch >= 3 ? FramePacket.ColorMode.RGB : FramePacket.ColorMode.GRAYSCALE;
        frame.allocate(w, h, mode);
        int srcCh = Math.max(1, ch);
        byte[] data = new byte[w * h * srcCh];
        mat.get(0, 0, data);
        short[] pix = frame.getPixels();
        int dstCh = frame.channelsPerPixel();
        for (int y = 0; y < h; y++) {
            int srcY = h - 1 - y;
            for (int x = 0; x < w; x++) {
                int si = (srcY * w + x) * srcCh;
                int di = (y * w + x) * dstCh;
                if (dstCh >= 3 && srcCh >= 3) {
                    pix[di] = (short) (data[si + 2] & 0xff);
                    pix[di + 1] = (short) (data[si + 1] & 0xff);
                    pix[di + 2] = (short) (data[si] & 0xff);
                } else {
                    pix[di] = (short) (data[si] & 0xff);
                }
            }
        }
    }

    static FramePacket copyFrame(FramePacket src) {
        FramePacket out = new FramePacket();
        out.allocate(src.getWidth(), src.getHeight(), src.getColorMode());
        short[] a = src.getPixels();
        short[] b = out.getPixels();
        if (a != null && b != null) {
            System.arraycopy(a, 0, b, 0, Math.min(a.length, b.length));
        }
        out.setTimestampStartUs(src.getTimestampStartUs());
        out.setTimestampEndUs(src.getTimestampEndUs());
        out.setExposureUs(src.getExposureUs());
        return out;
    }

    @Override
    public void addAEListener(AEListener listener) {
        support.addPropertyChangeListener(listener);
    }

    @Override
    public void removeAEListener(AEListener listener) {
        support.removePropertyChangeListener(listener);
    }

    @Override
    public int getMaxCapacity() {
        return 120;
    }

    @Override
    public int getEstimatedEventRate() {
        return estimatedRate;
    }

    @Override
    public int getTimestampTickUs() {
        return 1;
    }

    @Override
    public void setChip(AEChip chip) {
        this.chip = chip;
    }

    @Override
    public AEChip getChip() {
        return chip;
    }
}
