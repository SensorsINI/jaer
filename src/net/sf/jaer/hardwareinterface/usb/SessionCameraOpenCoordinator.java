/*
 * SessionCameraOpenCoordinator.java
 */
package net.sf.jaer.hardwareinterface.usb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.util.ViewerInterfaceBindingMap;

/**
 * Blocks USB until every session AEViewer is visible and
 * {@link net.sf.jaer.util.WindowSaver} has applied saved bounds. After that,
 * session-restored viewers may autobind; File → New stays WAITING until
 * Interface. Native {@code open()} is serialized by
 * {@code AEViewer.USB_OPEN_SERIAL_LOCK}, not by a second token machine.
 *
 * <p>Classic FX3 DVXplorer is not autobound (WinUSB SPI hang). Interface may
 * still select it. A hung classic SPI does not skip that camera forever.
 */
public final class SessionCameraOpenCoordinator {

    public enum Phase {
        UI_RESTORE,
        RUNNING
    }

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Object LOCK = new Object();
    /** Empty factory list is common on the first Windows scan. */
    private static final int EMPTY_SCANS_BEFORE_RESTORE_DONE = 5;

    private static Phase phase = Phase.RUNNING;
    private static JAERViewer sessionViewer;
    private static AEViewer userGrant;
    private static final Set<AEViewer> restorePending = new HashSet<>();
    private static final java.util.Map<AEViewer, Integer> emptyScans = new java.util.IdentityHashMap<>();
    /** Already logged LIVE acquire for this viewer (ViewLoop calls every tick). */
    private static final Set<AEViewer> acquiringLogged = java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<>());

    private SessionCameraOpenCoordinator() {
    }

    public static Phase phase() {
        synchronized (LOCK) {
            return phase;
        }
    }

    /** CLI / {@link USBRebindTester} snapshot of the session gate. */
    public static String dump() {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder();
            sb.append("coordinator phase=").append(phase);
            sb.append(" session=").append(sessionViewer == null ? "-" : "JAERViewer");
            sb.append(" grant=").append(label(userGrant));
            sb.append(" restorePending=").append(restorePending.size());
            sb.append('\n');
            return sb.toString();
        }
    }

    private static String label(AEViewer v) {
        return v == null ? "-" : v.getViewerWindowLabel();
    }

    public static boolean isUiRestore() {
        synchronized (LOCK) {
            return phase == Phase.UI_RESTORE;
        }
    }

    /** True after windows are placed; session viewers may autobind. */
    public static boolean isRunning() {
        synchronized (LOCK) {
            return phase == Phase.RUNNING;
        }
    }

    /**
     * Call on the EDT before constructing session AEViewers. ViewLoops must
     * not open USB until {@link #uiRestoreComplete}.
     */
    public static void beginUiRestore(JAERViewer jv) {
        synchronized (LOCK) {
            sessionViewer = jv;
            phase = Phase.UI_RESTORE;
            userGrant = null;
            restorePending.clear();
            emptyScans.clear();
        }
        UsbOpenTrace.event("ui-restore", "no USB until WindowSaver places all AEViewers",
                jv == null ? "null" : "JAERViewer");
        log.info("USB session: UI restore — cameras stay closed until windows are placed");
    }

    /**
     * WindowSaver has applied bounds. Off-EDT pre-scan, then RUNNING so
     * session viewers can autobind (classic FX3 DVX last).
     */
    public static void uiRestoreComplete(JAERViewer jv) {
        Thread t = new Thread(() -> {
            try {
                net.sf.jaer.hardwareinterface.HardwareInterfaceFactory.instance().markUsbEnumerationDirty();
                net.sf.jaer.hardwareinterface.HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
            } catch (Throwable e) {
                log.warning("USB session: pre-scan after UI restore: " + e);
            }
            enterRunning(jv);
        }, "jaer-usb-session-prescan");
        t.setDaemon(true);
        t.start();
    }

    private static void enterRunning(JAERViewer jv) {
        List<AEViewer> snapshot;
        synchronized (LOCK) {
            sessionViewer = jv;
            snapshot = jv == null ? List.of() : new ArrayList<>(jv.getViewers());
            restorePending.clear();
            emptyScans.clear();
            for (AEViewer v : snapshot) {
                if (v != null && v.isAutobindOnWaiting()) {
                    restorePending.add(v);
                }
            }
            phase = Phase.RUNNING;
        }
        UsbOpenTrace.event("running", "session viewers may autobind; classic DVX last; USB_OPEN_SERIAL_LOCK",
                snapshot.size() + " viewers pending=" + restorePending.size());
        log.info("USB session: running — " + snapshot.size() + " viewers, "
                + restorePending.size() + " autobind");
        for (AEViewer v : snapshot) {
            v.interruptViewloop();
        }
    }

    /**
     * Classic FX3 DVXplorer (not Mini/Micro). Does not {@code LibUsb.open}.
     */
    public static boolean isClassicDvxHardware(HardwareInterface hw) {
        return hw instanceof net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.DVXplorerFX3HardwareInterface dvx
                && !dvx.isMipiCX3Device();
    }

    /**
     * Classic FX3 DVXplorer (not Mini/Micro). Match remembered map or the
     * already-bound wrapper; does not {@code LibUsb.open}.
     */
    public static boolean isClassicDvxViewer(AEViewer v) {
        if (v == null) {
            return false;
        }
        HardwareInterface bound = v.getChip() == null ? null : v.getChip().getHardwareInterface();
        if (isClassicDvxHardware(bound)) {
            return true;
        }
        ViewerInterfaceBindingMap.Binding b = ViewerInterfaceBindingMap.get(v.getViewerInstanceIndex());
        if (b == null) {
            return false;
        }
        try {
            var factory = net.sf.jaer.hardwareinterface.HardwareInterfaceFactory.instance();
            int n = factory.getCachedNumInterfacesAvailable();
            for (int i = 0; i < n; i++) {
                HardwareInterface hw = factory.getInterface(i);
                if (hw instanceof net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.DVXplorerFX3HardwareInterface dvx
                        && b.matches(hw)) {
                    return !dvx.isMipiCX3Device();
                }
            }
        } catch (Throwable t) {
            log.fine("classic DVX check: " + t);
        }
        return false;
    }

    /**
     * Classic FX3 DVX waits until other session cameras have bound+opened or
     * given up, so Mini / DVS / Davis reach LIVE first.
     */
    public static boolean shouldDeferClassicDvxOpen(AEViewer v) {
        if (v == null || !isClassicDvxViewer(v)) {
            return false;
        }
        List<AEViewer> pending;
        synchronized (LOCK) {
            pending = new ArrayList<>(restorePending);
        }
        for (AEViewer other : pending) {
            if (other != null && other != v && !isClassicDvxViewer(other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if this viewer may scan, bind, or {@code open()} USB now.
     * LIVE may reopen its own camera without a new grant.
     */
    public static boolean mayOpenUsb(AEViewer v) {
        if (v == null) {
            return true;
        }
        synchronized (LOCK) {
            if (phase == Phase.UI_RESTORE) {
                return false;
            }
            if (v.getPlayMode() == AEViewer.PlayMode.LIVE) {
                return true;
            }
            if (userGrant == v) {
                return true;
            }
            return v.isAutobindOnWaiting();
        }
    }

    public static boolean hasOpenGrant(AEViewer v) {
        synchronized (LOCK) {
            return v != null && v == userGrant;
        }
    }

    /** Status line while this viewer must not open USB; null if it may proceed. */
    public static String waitReason(AEViewer v) {
        synchronized (LOCK) {
            if (phase == Phase.UI_RESTORE) {
                return "Waiting for windows to finish restoring…";
            }
        }
        if (shouldDeferClassicDvxOpen(v)) {
            return "Waiting for other cameras before classic DVX…";
        }
        return null;
    }

    /**
     * Interface menu or Refresh on this window. Always succeeds: the click
     * is never dropped. If another camera holds {@code USB_OPEN_SERIAL_LOCK},
     * this viewer waits on that lock inside {@code openAEMonitor}.
     *
     * @return always {@code true}
     */
    public static boolean userRequestedOpen(AEViewer v) {
        if (v == null) {
            return true;
        }
        synchronized (LOCK) {
            userGrant = v;
            acquiringLogged.remove(v);
        }
        UsbOpenTrace.event("user-open", "Interface/Refresh; this viewer opens (serial lock if busy)",
                v.getViewerWindowLabel());
        v.interruptViewloop();
        return true;
    }

    public static void userCancelledOpen(AEViewer v) {
        if (v == null) {
            return;
        }
        synchronized (LOCK) {
            if (userGrant == v) {
                userGrant = null;
            }
            restorePending.remove(v);
            emptyScans.remove(v);
            acquiringLogged.remove(v);
        }
        UsbOpenTrace.event("user-none", "Interface → None", v.getViewerWindowLabel());
    }

    /** This viewer's camera left the bus; allow remembered rebind. */
    public static void allowRememberedRebind(AEViewer v) {
        userRequestedOpen(v);
    }

    /**
     * No bindable camera this tick. Does <em>not</em> set {@code nullInterface}.
     * After several empty scans this viewer is no longer “restore pending”
     * so classic DVX can proceed.
     *
     * @return always {@code false} (keep WAITING polling)
     */
    public static boolean noteEmptyBind(AEViewer v) {
        if (v == null) {
            return false;
        }
        synchronized (LOCK) {
            int n = emptyScans.getOrDefault(v, 0) + 1;
            emptyScans.put(v, n);
            if (n >= EMPTY_SCANS_BEFORE_RESTORE_DONE) {
                restorePending.remove(v);
            }
        }
        return false;
    }

    /** LIVE acquire returned. AEReader is running. */
    public static void noteAcquiring(AEViewer v) {
        viewerFinishedOpenAttempt(v, "live-acquiring");
    }

    /**
     * This viewer's open attempt is done. Clears Interface grant. Idempotent.
     */
    public static void viewerFinishedOpenAttempt(AEViewer v, String outcome) {
        if (v == null) {
            return;
        }
        synchronized (LOCK) {
            if (userGrant == v) {
                userGrant = null;
            }
            restorePending.remove(v);
            emptyScans.remove(v);
            if ("live-acquiring".equals(outcome)) {
                if (!acquiringLogged.add(v)) {
                    return;
                }
            } else {
                acquiringLogged.remove(v);
            }
        }
        // TODO comment out once USB session restore is stable (LIVE called this every tick).
        UsbOpenTrace.event("open-done", outcome, v.getViewerWindowLabel());
        log.fine("USB session: " + v.getViewerWindowLabel() + " " + outcome);
    }

    /** Kept for WAITING callers; no token watchdog. */
    public static void pollWatchdog() {
        // USB_OPEN_SERIAL_LOCK plus per-camera open timeout replace the old token deadline.
    }
}
