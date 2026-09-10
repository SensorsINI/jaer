package net.sf.jaer.hardwareinterface.opencv;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;
import net.sf.jaer.util.OpenCVNativeLoader;

/**
 * Cached OpenCV {@code VideoCapture} device list. Probe is never on the EDT and
 * is not part of every USB {@code buildInterfaceList()} poll.
 */
public final class OpenCvCameraFactory implements HardwareInterfaceFactoryInterface {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    public static final int MAX_INDEX = 8;

    private static final OpenCvCameraFactory INSTANCE = new OpenCvCameraFactory();

    private volatile List<DeviceInfo> snapshot = List.of();
    private final AtomicBoolean probeQueued = new AtomicBoolean(false);
    private volatile boolean probeStarted;
    /** Tests set false so {@link #probeNow()} does not call {@code VideoCapture.open}. */
    private volatile boolean enumerationEnabled = true;

    public static final class DeviceInfo {
        public final int index;
        public final int api;
        public final String label;
        public final int width;
        public final int height;
        public final String backend;
        public final double fps;
        public final String fourcc;
        public final String tooltipHtml;

        public DeviceInfo(int index, int api, String label, int width, int height) {
            this(index, api, label, width, height, "", 0, "");
        }

        public DeviceInfo(int index, int api, String label, int width, int height,
                String backend, double fps, String fourcc) {
            this.index = index;
            this.api = api;
            this.label = label == null ? ("OpenCV " + index) : label;
            this.width = width;
            this.height = height;
            this.backend = backend == null ? "" : backend;
            this.fps = fps;
            this.fourcc = fourcc == null ? "" : fourcc;
            this.tooltipHtml = tooltipHtml(index, api, this.backend, width, height, fps, this.fourcc);
        }
    }

    private OpenCvCameraFactory() {
        requestProbe();
    }

    public static HardwareInterfaceFactoryInterface instance() {
        return INSTANCE;
    }

    public static OpenCvCameraFactory factory() {
        return INSTANCE;
    }

    public void setEnumerationEnabled(boolean enabled) {
        enumerationEnabled = enabled;
    }

    public boolean isEnumerationEnabled() {
        return enumerationEnabled;
    }

    /** Cached devices from the last completed probe (may be empty). */
    public List<DeviceInfo> cachedDevices() {
        return snapshot;
    }

    /**
     * Kick a background probe. Safe to call from the factory constructor and
     * after UI startup. Does not block.
     */
    public void requestProbe() {
        if (!enumerationEnabled) {
            return;
        }
        if (!probeQueued.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                probeNow();
            } finally {
                probeQueued.set(false);
            }
        }, "jaer-opencv-enum");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Probe indices {@code 0..MAX_INDEX-1} on this thread. Interface → Refresh
     * runs this off the EDT before rebuilding the USB snapshot.
     */
    public void probeNow() {
        if (!enumerationEnabled) {
            return;
        }
        probeStarted = true;
        if (!OpenCVNativeLoader.load()) {
            snapshot = List.of();
            return;
        }
        int api = preferredApi();
        List<DeviceInfo> found = new ArrayList<>();
        for (int i = 0; i < MAX_INDEX; i++) {
            if (OpenCvCameraHardwareInterface.isIndexOpen(i)) {
                DeviceInfo prev = previous(i);
                found.add(prev != null ? prev
                        : new DeviceInfo(i, api, "OpenCV: " + i + " (open)", 0, 0));
                continue;
            }
            DeviceInfo info = tryOpen(i, api);
            if (info == null && api != Videoio.CAP_ANY) {
                info = tryOpen(i, Videoio.CAP_ANY);
            }
            if (info != null) {
                found.add(info);
            }
        }
        snapshot = List.copyOf(found);
        log.info("OpenCV cameras cached: " + snapshot.size());
        try {
            HardwareInterfaceFactory.instance().markUsbEnumerationDirty();
            HardwareInterfaceFactory.instance().requestBackgroundScan();
        } catch (Exception e) {
            log.log(Level.FINE, "OpenCV enum: USB rescan after probe: " + e, e);
        }
    }

    private DeviceInfo previous(int index) {
        for (DeviceInfo d : snapshot) {
            if (d.index == index) {
                return d;
            }
        }
        return null;
    }

    static int preferredApi() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Videoio.CAP_DSHOW;
        }
        if (os.contains("mac")) {
            return Videoio.CAP_AVFOUNDATION;
        }
        return Videoio.CAP_V4L2;
    }

    private static DeviceInfo tryOpen(int index, int api) {
        VideoCapture cap = new VideoCapture();
        try {
            if (!cap.open(index, api)) {
                return null;
            }
            if (!cap.isOpened()) {
                return null;
            }
            int w = (int) cap.get(Videoio.CAP_PROP_FRAME_WIDTH);
            int h = (int) cap.get(Videoio.CAP_PROP_FRAME_HEIGHT);
            String backend = "";
            try {
                backend = cap.getBackendName();
            } catch (Exception e) {
                log.log(Level.FINE, "OpenCV backend name: " + e, e);
            }
            String label = "OpenCV: " + index
                    + (backend.isEmpty() ? "" : " " + backend);
            if (w > 0 && h > 0) {
                label += " " + w + "x" + h;
            }
            double fps = 0;
            String fourcc = "";
            try {
                fps = cap.get(Videoio.CAP_PROP_FPS);
                fourcc = fourccName(cap.get(Videoio.CAP_PROP_FOURCC));
            } catch (Exception e) {
                log.log(Level.FINE, "OpenCV fps/fourcc: " + e, e);
            }
            return new DeviceInfo(index, api, label, w, h, backend, fps, fourcc);
        } catch (Exception e) {
            log.log(Level.FINE, "OpenCV probe index " + index + " api=" + api + ": " + e, e);
            return null;
        } finally {
            try {
                cap.release();
            } catch (Exception e) {
                log.log(Level.FINE, "OpenCV probe release: " + e, e);
            }
        }
    }

    @Override
    public int getNumInterfacesAvailable() {
        return snapshot.size();
    }

    @Override
    public HardwareInterface getFirstAvailableInterface() {
        return getInterface(0);
    }

    @Override
    public HardwareInterface getInterface(int n) {
        List<DeviceInfo> list = snapshot;
        if (n < 0 || n >= list.size()) {
            return null;
        }
        DeviceInfo d = list.get(n);
        return new OpenCvCameraHardwareInterface(d);
    }

    @Override
    public String getGUID() {
        return null;
    }

    /**
     * HTML tooltip for Interface menu: backend, size, fps, pixel format, and
     * that selecting the webcam occupies it for other apps.
     */
    public static String tooltipHtml(int index, int api, String backend, int width, int height,
            double fps, String fourcc) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("OpenCV camera index ").append(index);
        sb.append(" (0 is the first webcam OpenCV finds)");
        String be = humanBackend(backend, api);
        if (!be.isEmpty()) {
            sb.append("<br>").append(escapeHtml(be));
        }
        StringBuilder geo = new StringBuilder();
        if (width > 0 && height > 0) {
            geo.append(width).append(" × ").append(height);
        }
        String fpsTxt = formatFps(fps);
        if (!fpsTxt.isEmpty()) {
            if (geo.length() > 0) {
                geo.append(" at ");
            }
            geo.append(fpsTxt);
        }
        String fcc = fourcc == null ? "" : fourcc.trim();
        if (!fcc.isEmpty()) {
            if (geo.length() > 0) {
                geo.append(" · ");
            }
            geo.append("pixel format ").append(escapeHtml(fcc));
        }
        if (geo.length() > 0) {
            sb.append("<br>").append(geo);
        }
        sb.append("<br>Selecting this occupies the camera so Zoom, Meet, and other apps cannot use it");
        sb.append(" until Interface → None.");
        sb.append("<br>The index is OpenCV's camera number, not a USB serial. Interface → Refresh after plugging another webcam.");
        return sb.toString();
    }

    /** Pack a four-character code for {@code CAP_PROP_FOURCC} ({@code MJPG}, {@code YUY2}). */
    public static int fourccCode(String name) {
        if (name == null) {
            return 0;
        }
        String s = name.trim();
        if (s.length() < 4) {
            return 0;
        }
        return (s.charAt(0) & 0xff)
                | ((s.charAt(1) & 0xff) << 8)
                | ((s.charAt(2) & 0xff) << 16)
                | ((s.charAt(3) & 0xff) << 24);
    }

    public static String fourccName(double raw) {
        int c = (int) Math.round(raw);
        if (c <= 0) {
            return "";
        }
        char[] ch = new char[4];
        for (int i = 0; i < 4; i++) {
            int b = (c >> (8 * i)) & 0xff;
            if (b < 32 || b > 126) {
                return "";
            }
            ch[i] = (char) b;
        }
        return new String(ch).trim();
    }

    static String formatFps(double fps) {
        if (!(fps > 0.5) || fps > 1000) {
            return "";
        }
        if (Math.abs(fps - Math.round(fps)) < 0.05) {
            return ((int) Math.round(fps)) + " fps";
        }
        return String.format(Locale.ROOT, "%.1f fps", fps);
    }

    static String humanBackend(String backend, int api) {
        String raw = backend == null ? "" : backend.trim();
        String u = raw.toUpperCase(Locale.ROOT);
        if (u.contains("DSHOW") || api == Videoio.CAP_DSHOW) {
            return "DirectShow (Windows webcam API)";
        }
        if (u.contains("MSMF") || api == Videoio.CAP_MSMF) {
            return "Media Foundation (Windows webcam API)";
        }
        if (u.contains("AVFOUNDATION") || api == Videoio.CAP_AVFOUNDATION) {
            return "AVFoundation (macOS camera API)";
        }
        if (u.contains("V4L") || api == Videoio.CAP_V4L2) {
            return "V4L2 (Linux camera API)";
        }
        if (!raw.isEmpty()) {
            return "OpenCV backend " + raw;
        }
        if (api == Videoio.CAP_ANY) {
            return "OpenCV default camera backend";
        }
        return "";
    }

    static String escapeHtml(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    boolean hasStartedProbe() {
        return probeStarted;
    }
}
