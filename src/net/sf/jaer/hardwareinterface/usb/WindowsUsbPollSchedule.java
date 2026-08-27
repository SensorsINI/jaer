package net.sf.jaer.hardwareinterface.usb;

import java.util.logging.Logger;

/**
 * Decaying USB bus-scan interval for platforms without libusb hotplug
 * (Windows WinUSB in bundled libusb 1.0.22).
 * <p>
 * After startup, window focus, or any enumerated-device change: scan every
 * {@link #FAST_INTERVAL_MS} for {@link #FAST_DURATION_MS}, then every
 * {@link #MEDIUM_INTERVAL_MS} for {@link #MEDIUM_DURATION_MS}, then every
 * {@link #SLOW_INTERVAL_MS}. Linux/macOS use {@link LibUsbHotplug} instead.
 */
public final class WindowsUsbPollSchedule {

    public static final long FAST_INTERVAL_MS = 1_000L;
    public static final long MEDIUM_INTERVAL_MS = 3_000L;
    public static final long SLOW_INTERVAL_MS = 15_000L;
    /** 1 s scans last this long after startup or a device-list change. */
    public static final long FAST_DURATION_MS = 60_000L;
    /** 3 s scans last this long after the fast window. */
    public static final long MEDIUM_DURATION_MS = 10 * 60_000L;

    static final long WAITING_SLEEP_MAX_MS = 600L;
    static final long WAITING_SLEEP_MIN_MS = 200L;

    private long epochMs;
    private long lastLoggedIntervalMs = -1L;
    private String lastFingerprint;
    private boolean haveFingerprint;

    public WindowsUsbPollSchedule() {
        this(System.currentTimeMillis());
    }

    /** Visible for tests; {@code epochMs} is the start of the current decay. */
    public WindowsUsbPollSchedule(long epochMs) {
        this.epochMs = epochMs;
    }

    public synchronized long intervalMs(long nowMs) {
        long age = Math.max(0L, nowMs - epochMs);
        if (age < FAST_DURATION_MS) {
            return FAST_INTERVAL_MS;
        }
        if (age < FAST_DURATION_MS + MEDIUM_DURATION_MS) {
            return MEDIUM_INTERVAL_MS;
        }
        return SLOW_INTERVAL_MS;
    }

    /**
     * ViewLoop WAITING sleep so a 1 s poll is actually reachable (the loop
     * otherwise slept a fixed 600 ms).
     */
    public synchronized long waitingSleepMs(long nowMs) {
        long interval = intervalMs(nowMs);
        return Math.min(WAITING_SLEEP_MAX_MS, Math.max(WAITING_SLEEP_MIN_MS, interval / 2));
    }

    /** INFO when the poll interval steps 1 s → 3 s → 15 s. */
    public synchronized void logPhaseIfChanged(long nowMs, Logger log) {
        long interval = intervalMs(nowMs);
        if (interval == lastLoggedIntervalMs) {
            return;
        }
        lastLoggedIntervalMs = interval;
        log.info(phaseMessage(interval));
    }

    /**
     * After a bus scan: if the enumerated set changed, restart the 1 s window.
     *
     * @return true if the schedule was reset
     */
    public synchronized boolean noteScanResult(String fingerprint, long nowMs, Logger log) {
        if (fingerprint == null) {
            fingerprint = "none";
        }
        if (!haveFingerprint) {
            lastFingerprint = fingerprint;
            haveFingerprint = true;
            return false;
        }
        if (fingerprint.equals(lastFingerprint)) {
            return false;
        }
        String previous = lastFingerprint;
        lastFingerprint = fingerprint;
        reset("device status changed (" + previous + " -> " + fingerprint + ")", nowMs, log);
        return true;
    }

    /**
     * Restart the decay (startup is the constructor; also unplug, device-gone,
     * or AEViewer window focus).
     */
    public synchronized void reset(String reason, long nowMs, Logger log) {
        boolean alreadyFast = intervalMs(nowMs) == FAST_INTERVAL_MS;
        epochMs = nowMs;
        haveFingerprint = false;
        lastFingerprint = null;
        lastLoggedIntervalMs = FAST_INTERVAL_MS;
        if (!alreadyFast) {
            log.info("Windows USB poll: " + reason + "; scanning every 1 s for 1 min");
        }
    }

    static String phaseMessage(long intervalMs) {
        if (intervalMs <= FAST_INTERVAL_MS) {
            return "Windows USB poll: scanning every 1 s (first 1 min after startup or device change)";
        }
        if (intervalMs <= MEDIUM_INTERVAL_MS) {
            return "Windows USB poll: scanning every 3 s (next 10 min)";
        }
        return "Windows USB poll: scanning every 15 s";
    }
}
