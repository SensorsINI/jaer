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

        public DeviceInfo(int index, int api, String label, int width, int height) {
            this.index = index;
            this.api = api;
            this.label = label == null ? ("OpenCV " + index) : label;
            this.width = width;
            this.height = height;
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
            String backend = cap.getBackendName();
            String label = "OpenCV: " + index
                    + (backend == null || backend.isEmpty() ? "" : " " + backend);
            if (w > 0 && h > 0) {
                label += " " + w + "x" + h;
            }
            return new DeviceInfo(index, api, label, w, h);
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
        return new OpenCvCameraHardwareInterface(d.index, d.api, d.label, d.width, d.height);
    }

    @Override
    public String getGUID() {
        return null;
    }

    boolean hasStartedProbe() {
        return probeStarted;
    }
}
