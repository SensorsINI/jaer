package net.sf.jaer.hardwareinterface.usb;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.HotplugCallback;
import org.usb4java.LibUsb;
import org.usb4java.Version;

import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;

/**
 * Process-wide libusb hotplug support for camera discovery.
 * <p>
 * Linux (and macOS) libusb reports {@link LibUsb#CAP_HAS_HOTPLUG}; the WinUSB
 * backend in bundled libusb 1.0.22 does not (Windows hotplug arrived in libusb
 * 1.0.27). Callbacks only run when some thread pumps
 * {@link LibUsb#handleEventsTimeout}; {@code USBTransferThread} does that after
 * a camera is open. This class starts a daemon pump at factory init so WAITING
 * discovery does not depend on a live reader.
 * <p>
 * Listeners are invoked off the libusb thread. Do not call libusb from a
 * listener except as documented safe for hotplug callbacks.
 */
public final class LibUsbHotplug {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    /** {@link LibUsb#handleEventsTimeout} unit is microseconds. */
    private static final long EVENT_TIMEOUT_US = 250_000L;

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Set<ImmutablePair<Short, Short>> registeredVidPid = new CopyOnWriteArraySet<>();
    private static final ExecutorService notifyExecutor = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "LibUsb-hotplug-notify");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean started;
    private static volatile boolean supported;
    private static Thread eventThread;

    private LibUsbHotplug() {
    }

    /** Called after a matching camera arrives or leaves. */
    @FunctionalInterface
    public interface Listener {
        void usbDeviceChanged(boolean arrived, int vid, int pid);
    }

    /**
     * Whether this process can receive libusb hotplug callbacks.
     * Safe before {@link LibUsb#init}; returns false if natives are missing.
     */
    public static boolean isSupported() {
        try {
            return LibUsb.hasCapability(LibUsb.CAP_HAS_HOTPLUG);
        } catch (UnsatisfiedLinkError | Exception e) {
            return false;
        }
    }

    /**
     * Start the default-context event pump once. Call after {@link LibUsb#init}.
     */
    public static synchronized void ensureStarted() {
        if (started) {
            return;
        }
        started = true;
        supported = isSupported();
        String version = "?";
        try {
            final Version v = LibUsb.getVersion();
            if (v != null) {
                version = v.major() + "." + v.minor() + "." + v.micro();
            }
        } catch (Exception e) {
            log.log(Level.FINE, "Could not read libusb version", e);
        }
        if (!supported) {
            log.info("LibUsb hotplug not supported (libusb " + version
                    + "); typical on Windows WinUSB with libusb 1.0.22. "
                    + "Camera discovery keeps periodic USB bus scans.");
            return;
        }
        eventThread = new Thread(LibUsbHotplug::pumpEvents, "LibUsb-hotplug");
        eventThread.setDaemon(true);
        eventThread.start();
        log.info("LibUsb hotplug event pump started (libusb " + version
                + "); WAITING discovery uses plug events instead of a 3 s full bus scan.");
    }

    public static void addListener(Listener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Register a VID/PID hotplug callback on the default context. Idempotent.
     */
    public static void register(short vid, short pid) {
        ensureStarted();
        if (!supported) {
            return;
        }
        final ImmutablePair<Short, Short> key = new ImmutablePair<>(vid, pid);
        if (!registeredVidPid.add(key)) {
            return;
        }
        final int vidInt = vid & 0xffff;
        final int pidInt = pid & 0xffff;
        final HotplugCallback callback = (Context cntxt, Device device, int event, Object userData) -> {
            final DeviceDescriptor descriptor = new DeviceDescriptor();
            final int errCode = LibUsb.getDeviceDescriptor(device, descriptor);
            final int v;
            final int p;
            if (errCode == LibUsb.SUCCESS) {
                v = descriptor.idVendor() & 0xffff;
                p = descriptor.idProduct() & 0xffff;
            } else {
                v = vidInt;
                p = pidInt;
            }
            final boolean arrived = event == LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED;
            log.info(String.format("LibUsb hotplug %s %04x:%04x", arrived ? "Connected" : "Disconnected", v, p));
            // Mark dirty on the libusb thread so WAITING can rescan even if notify is delayed.
            HardwareInterfaceFactory.instance().markUsbEnumerationDirty();
            notifyDeviceChange(arrived, v, p);
            return 0;
        };
        final int errCode = LibUsb.hotplugRegisterCallback(null,
                LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED | LibUsb.HOTPLUG_EVENT_DEVICE_LEFT,
                LibUsb.HOTPLUG_ENUMERATE,
                vidInt, pidInt, LibUsb.HOTPLUG_MATCH_ANY,
                callback, null, null);
        if (errCode != LibUsb.SUCCESS) {
            registeredVidPid.remove(key);
            log.warning(String.format("Could not register LibUsb hotplug callback for %04x:%04x: %s",
                    vid & 0xffff, pid & 0xffff, LibUsb.errorName(errCode)));
        }
    }

    private static void notifyDeviceChange(boolean arrived, int vid, int pid) {
        notifyExecutor.execute(() -> {
            HardwareInterfaceFactory.instance().markUsbEnumerationDirty();
            // Rebuild off-EDT so the Interface menu cache includes a camera
            // plugged in while LIVE (WAITING does not scan). A stale Device
            // wrapper then fails NRV claimInterface with NOT_FOUND (Linux).
            try {
                HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
            } catch (Exception e) {
                log.log(Level.WARNING, "LibUsb hotplug rescan failed", e);
            }
            if (listeners.isEmpty()) {
                log.fine("LibUsb hotplug: no AEViewer listener registered yet");
            }
            for (Listener listener : listeners) {
                try {
                    listener.usbDeviceChanged(arrived, vid, pid);
                } catch (Exception e) {
                    log.log(Level.WARNING, "LibUsb hotplug listener failed", e);
                }
            }
        });
    }

    private static void pumpEvents() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                final int rc = LibUsb.handleEventsTimeout(null, EVENT_TIMEOUT_US);
                if (rc != LibUsb.SUCCESS) {
                    log.warning("LibUsb.handleEventsTimeout: " + LibUsb.errorName(rc));
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.log(Level.WARNING, "LibUsb hotplug event pump error", e);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
