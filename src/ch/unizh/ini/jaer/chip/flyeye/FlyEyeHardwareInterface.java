/*
 * FlyEyeHardwareInterface.java
 *
 * Two DVS128 USB interfaces claimed by one FlyEye AEChip. Does not open USB in
 * the chip getter; ViewLoop openAEMonitor opens this composite.
 */
package ch.unizh.ini.jaer.chip.flyeye;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.FlyEyeEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.UsbIds;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2DVS128HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasSyncEventOutput;
import net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2LibUsbDVS128HardwareInterface;
import net.sf.jaer.stereopsis.StereoBiasgenHardwareInterface;

/**
 * Pair of DVS128 monitors merged into one FlyEye stream.
 */
public class FlyEyeHardwareInterface extends StereoBiasgenHardwareInterface {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static String lastMissingPairLogKey;
    private final FlyEye flyEye;
    private final EventPacket<FlyEyeEvent> merged0 = new EventPacket<>(FlyEyeEvent.class);
    private final EventPacket<FlyEyeEvent> merged1 = new EventPacket<>(FlyEyeEvent.class);
    private final PacketBundle bundle0 = new PacketBundle();
    private final PacketBundle bundle1 = new PacketBundle();
    private boolean useSlot0 = true;
    private long lastEmptyMergeLogMs;
    private long lastDesyncLogMs;
    private int lastDesyncLeftTs = Integer.MIN_VALUE;
    private int lastDesyncRightTs = Integer.MIN_VALUE;
    private boolean loggedSlaveStopped;
    private JDialog desyncDialog;
    private JOptionPane desyncPane;
    private boolean hidingDesyncBecauseRecovered;
    private long desyncDialogSuppressedUntilMs;
    private long oneSidedEmptySinceMs;
    private long lastDesyncMessageMs;
    private String lastDesyncHtml;
    private static final long DESYNC_DIALOG_SUPPRESS_MS = 10_000L;
    private PropertyChangeListener playModeListener;
    private AEViewer playModeViewer;
    private boolean eyesAssigned;
    private String lastLoggedSyncSettings;
    /** Log / advertised limit. Live USB dt chatters around this (~100.2–103 ms in jAER-0.log). */
    private static final int DESYNC_WARN_US = 100_000;
    /** Show only after crossing this (hysteresis above the 100 ms chatter). */
    private static final int DESYNC_SHOW_US = 150_000;
    /** Hide only after recovering below this. */
    private static final int DESYNC_CLEAR_US = 50_000;
    /** One USB slice with no polarity from one eye is normal; wait this long. */
    private static final long ONE_SIDED_EMPTY_MS = 750L;
    private static final long DESYNC_MESSAGE_UPDATE_MS = 1000L;

    public FlyEyeHardwareInterface(FlyEye flyEye, AEMonitorInterface left, AEMonitorInterface right) {
        super(left, right);
        this.flyEye = flyEye;
        setChip(flyEye);
    }

    /**
     * Claim two unused DVS128 wrappers from the last USB snapshot. Does not
     * {@code LibUsb.open}. Returns null if fewer than two are free.
     */
    public static FlyEyeHardwareInterface claimUnusedDvs128Pair(FlyEye fly) {
        if (fly == null) {
            return null;
        }
        final FlyEyeHardwareInterface[] held = new FlyEyeHardwareInterface[1];
        AEViewer.runWithHardwareClaim(() -> held[0] = claimUnusedDvs128PairLocked(fly));
        return held[0];
    }

    private static FlyEyeHardwareInterface claimUnusedDvs128PairLocked(FlyEye fly) {
        if (fly.getAssignedHardwareInterface() instanceof FlyEyeHardwareInterface existing
                && !existing.isUnusableAfterUnplug()) {
            return existing;
        }
        HardwareInterfaceFactory factory = HardwareInterfaceFactory.instance();
        int n = factory.getCachedNumInterfacesAvailable();
        ArrayList<HardwareInterface> found = new ArrayList<>(4);
        for (int i = 0; i < n; i++) {
            HardwareInterface hw = factory.getInterface(i);
            if (!isDvs128Monitor(hw)) {
                continue;
            }
            if (hw instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2 fx2
                    && fx2.isUnopenableAfterUnplug()) {
                continue;
            }
            if (takenByOtherViewer(hw, fly.getAeViewer())) {
                continue;
            }
            found.add(hw);
        }
        if (found.size() < 2) {
            String key = found.size() + "/" + n;
            if (!key.equals(lastMissingPairLogKey)) {
                lastMissingPairLogKey = key;
                log.info("FlyEye needs 2 unused DVS128 cameras, found " + found.size()
                        + " among " + n + " enumerated interfaces");
            }
            return null;
        }
        lastMissingPairLogKey = null;
        found.sort(Comparator.comparing(UsbIds::enumerationKey));
        HardwareInterface hw0 = found.get(0);
        HardwareInterface hw1 = found.get(1);
        log.info("FlyEye claiming left=" + UsbIds.enumerationKey(hw0)
                + " right=" + UsbIds.enumerationKey(hw1)
                + (found.size() > 2 ? " (" + found.size() + " DVS128 available, using first two)" : ""));
        return new FlyEyeHardwareInterface(fly, (AEMonitorInterface) hw0, (AEMonitorInterface) hw1);
    }

    /** True after a child lost IN or abandoned its native handle; do not keep
     * this wrapper. A claimed-but-not-yet-opened pair is still usable. */
    public boolean isUnusableAfterUnplug() {
        return childUnusableAfterUnplug(getAemonLeft())
                || childUnusableAfterUnplug(getAemonRight());
    }

    private static boolean childUnusableAfterUnplug(AEMonitorInterface aemon) {
        return aemon instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2 fx2
                && fx2.isUnopenableAfterUnplug();
    }

    public static boolean isDvs128Monitor(HardwareInterface hw) {
        return hw instanceof AEMonitorInterface
                && (hw instanceof CypressFX2LibUsbDVS128HardwareInterface
                || hw instanceof CypressFX2DVS128HardwareInterface);
    }

    private static boolean takenByOtherViewer(HardwareInterface hw, AEViewer self) {
        if (self == null || self.getJaerViewer() == null) {
            return false;
        }
        for (AEViewer other : self.getJaerViewer().getViewers()) {
            if (other == null || other == self || other.getChip() == null) {
                continue;
            }
            HardwareInterface taken = other.getChip().getAssignedHardwareInterface();
            if (UsbIds.samePhysicalDevice(hw, taken)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setChip(AEChip chip) {
        this.chip = chip;
        if (chip instanceof FlyEye fly) {
            if (getAemonLeft() != null) {
                getAemonLeft().setChip(fly.getLeft());
            }
            if (getAemonRight() != null) {
                getAemonRight().setChip(fly.getRight());
            }
        } else {
            super.setChip(chip);
        }
    }

    private static final int TIMESTAMP_RESET_TRIES = 3;
    private static final long TIMESTAMP_RESET_WAIT_MS = 400L;
    private static final int TIMESTAMP_ALIGN_US = 10_000;
    /** Wait between polarity samples when checking a sync cable. */
    private static final long CABLING_TEST_WAIT_MS = 300L;
    /** Electrically synced cameras should stay well inside this after reset.
     * USB last-event times in two packets can still differ by a slice (~10 ms). */
    private static final int CABLING_SYNC_US = 20_000;
    private static final int CABLING_MIN_ADVANCE_US = 1;

    @Override
    public void open() throws HardwareInterfaceException {
        super.open();
        assignEyesBySerialAndPref();
        configureSyncMaster();
        logAssignment();
        attachPlayModeListener();
        showTimestampResetDialog(confirmTimestampResetBothCameras());
        // Vendor timestamp reset can restore firmware default (both masters).
        applySyncMaster(false);
    }

    /**
     * After USB open, assign left/right by serial (stable across port changes)
     * then apply the swap-eyes pref. Claim used bus/addr because serials are
     * not available until open.
     */
    private void assignEyesBySerialAndPref() {
        String id0 = serialOf(getAemonLeft());
        String id1 = serialOf(getAemonRight());
        if (id0 != null && id1 != null && id0.compareTo(id1) > 0) {
            swapAemonsOnly();
        }
        if (flyEye != null && flyEye.isEyesSwapped()) {
            swapAemonsOnly();
        }
        eyesAssigned = true;
    }

    /** Swap USB sides without reconfiguring sync (caller does that after open). */
    private void swapAemonsOnly() {
        AEMonitorInterface tmp = getAemonLeft();
        setAemonLeft(getAemonRight());
        setAemonRight(tmp);
        BiasgenHardwareInterfaceSwap();
        setChip(flyEye);
    }

    void configureSyncMaster() {
        applySyncMaster(true);
    }

    /**
     * RIGHT → left slave / right master; LEFT → opposite; NONE → both masters.
     * Always sends the vendor request (firmware can revert on timestamp reset).
     */
    private void applySyncMaster(boolean logInfo) {
        AEMonitorInterface left = getAemonLeft();
        AEMonitorInterface right = getAemonRight();
        FlyEye.TimestampMaster master = flyEye == null ? FlyEye.TimestampMaster.NONE
                : flyEye.getTimestampMaster();
        boolean wantLeftMaster = master != FlyEye.TimestampMaster.RIGHT;
        boolean wantRightMaster = master != FlyEye.TimestampMaster.LEFT;
        setSyncEnabled(left, wantLeftMaster, "left");
        setSyncEnabled(right, wantRightMaster, "right");
        boolean leftOn = syncEnabledOf(left);
        boolean rightOn = syncEnabledOf(right);
        String mode = switch (master) {
            case LEFT -> "left master, right slave";
            case RIGHT -> "right master, left slave";
            default -> "both DVS128s are timestamp masters (no sync cable)";
        };
        String summary = "FlyEye timestamp-master settings: " + mode
                + "; left=" + describe(left) + " syncEventEnabled=" + leftOn
                + " (" + (leftOn ? "master" : "slave") + ")"
                + "; right=" + describe(right) + " syncEventEnabled=" + rightOn
                + " (" + (rightOn ? "master" : "slave") + ")";
        if (leftOn != wantLeftMaster || rightOn != wantRightMaster) {
            log.warning(summary + " — mismatch wanted leftMaster=" + wantLeftMaster
                    + " rightMaster=" + wantRightMaster);
            lastLoggedSyncSettings = null;
        } else if (logInfo && !summary.equals(lastLoggedSyncSettings)) {
            lastLoggedSyncSettings = summary;
            log.info(summary);
        }
    }

    private static void setSyncEnabled(AEMonitorInterface aemon, boolean master, String side) {
        if (aemon instanceof HasSyncEventOutput sync) {
            sync.setSyncEventEnabled(master);
        } else if (aemon != null) {
            log.warning("FlyEye cannot set timestamp master on " + side + " ("
                    + aemon.getClass().getSimpleName() + ")");
        }
    }

    private static boolean syncEnabledOf(AEMonitorInterface aemon) {
        return aemon instanceof HasSyncEventOutput sync && sync.isSyncEventEnabled();
    }

    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        super.setEventAcquisitionEnabled(enable);
        // stopPlayback enables USB while playMode is still PLAYBACK, then LIVE.
        // Reset after IN is up so the firmware reset event is in the stream;
        // DVS128 drops leftover wrap URBs for 100 ms after that.
        if (enable && flyEye != null) {
            AEViewer v = flyEye.getAeViewer();
            if (v != null && v.getPlayMode() == AEViewer.PlayMode.PLAYBACK) {
                zeroTimestampsForResync("enabling LIVE after playback");
            }
        }
    }

    @Override
    public void close() {
        detachPlayModeListener();
        hideDesyncDialog(true);
        super.close();
    }

    @Override
    public synchronized void resetTimestamps() {
        // Do not call StereoPair.resetTimestamps(): it sets requestTimestampReset
        // so the next acquireAvailableEventsFromDriver re-enables USB IN and sleeps
        // 200 ms. ViewLoop uses PacketBundle; that flag stayed true after the first
        // LIVE resync and restarted the cameras during the second file playback.
        AEMonitorInterface left = getAemonLeft();
        AEMonitorInterface right = getAemonRight();
        if (left != null) {
            left.resetTimestamps();
        }
        if (right != null) {
            right.resetTimestamps();
        }
        if (eyesAssigned) {
            applySyncMaster(false);
        }
    }

    /**
     * StereoPair's raw acquire always turns IN on. During PLAYBACK that refills
     * the FX2 FIFOs and the next LIVE reset cannot drain them.
     */
    @Override
    public synchronized AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        if (!liveAcquisitionWanted()) {
            return new AEPacketRaw(0);
        }
        PacketBundle b = acquireAvailablePacketBundle();
        if (b == null) {
            return new AEPacketRaw(0);
        }
        AEPacketRaw raw = b.getRawPacket();
        return raw != null ? raw : new AEPacketRaw(0);
    }

    private void logAssignment() {
        log.info("FlyEye left=" + describe(getAemonLeft()) + " right=" + describe(getAemonRight()));
    }

    private static String serialOf(AEMonitorInterface aemon) {
        if (aemon instanceof USBInterface usb && aemon.isOpen()) {
            try {
                String[] sa = usb.getStringDescriptors();
                if (sa != null && sa.length > 2 && sa[2] != null && !sa[2].isBlank()) {
                    return sa[2];
                }
            } catch (Exception e) {
                // unopened or no string descriptors
            }
        }
        return null;
    }

    private static String describe(AEMonitorInterface aemon) {
        if (aemon == null) {
            return "null";
        }
        String key = UsbIds.enumerationKey(aemon);
        if (aemon.isOpen() && aemon instanceof USBInterface usb) {
            try {
                String[] sa = usb.getStringDescriptors();
                if (sa != null && sa.length > 2 && sa[2] != null && !sa[2].isBlank()) {
                    return key + " serial=" + sa[2];
                }
            } catch (Exception e) {
                // unopened or no string descriptors
            }
        }
        return key;
    }

    /**
     * Vendor-reset both cameras up to {@link #TIMESTAMP_RESET_TRIES} times and
     * wait for firmware reset events plus PacketBundle last timestamps within
     * {@link #TIMESTAMP_ALIGN_US}.
     */
    TimestampResetResult confirmTimestampResetBothCameras() {
        AEMonitorInterface left = getAemonLeft();
        AEMonitorInterface right = getAemonRight();
        if (left == null || right == null) {
            return TimestampResetResult.failed("missing camera");
        }
        TimestampResetResult last = TimestampResetResult.failed("no attempt");
        for (int attempt = 1; attempt <= TIMESTAMP_RESET_TRIES; attempt++) {
            long t0L = lastHardwareResetNanos(left);
            long t0R = lastHardwareResetNanos(right);
            left.resetTimestamps();
            right.resetTimestamps();
            applySyncMaster(false);
            drainChildBundles(left, right);
            long deadline = System.currentTimeMillis() + TIMESTAMP_RESET_WAIT_MS;
            boolean bothEvents = false;
            Long deltaUs = null;
            while (System.currentTimeMillis() < deadline) {
                bothEvents = lastHardwareResetNanos(left) > t0L
                        && lastHardwareResetNanos(right) > t0R;
                deltaUs = packetTimestampDeltaUs(left, right);
                if (deltaUs != null && Math.abs(deltaUs) <= TIMESTAMP_ALIGN_US) {
                    log.info("FlyEye timestamp reset confirmed: dt=" + deltaUs
                            + " us (attempt " + attempt + "/" + TIMESTAMP_RESET_TRIES
                            + ", bothResetEvents=" + bothEvents + ")");
                    return TimestampResetResult.aligned(deltaUs, bothEvents);
                }
                if (bothEvents && deltaUs == null) {
                    // Both firmware resets arrived; no polarity yet. Keep waiting
                    // until timeout for a 10 ms packet check.
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return TimestampResetResult.failed("interrupted");
                }
            }
            if (bothEvents && deltaUs == null) {
                // Firmware reset on both cameras is enough. Polarity often
                // arrives only after sendConfiguration (FlyEye pots started at 0).
                log.info("FlyEye timestamp reset: both cameras sent reset events (attempt "
                        + attempt + "/" + TIMESTAMP_RESET_TRIES
                        + "); no polarity packets yet to measure dt");
                return TimestampResetResult.resetEventsOnly();
            }
            last = deltaUs != null
                    ? TimestampResetResult.misaligned(deltaUs, bothEvents)
                    : TimestampResetResult.failed("no reset event from "
                            + (lastHardwareResetNanos(left) > t0L ? "right" : "left")
                            + " camera");
            log.warning("FlyEye timestamp reset attempt " + attempt + "/"
                    + TIMESTAMP_RESET_TRIES + ": " + last.detail);
        }
        return last;
    }

    private void drainChildBundles(AEMonitorInterface left, AEMonitorInterface right) {
        try {
            left.acquireAvailablePacketBundle();
            right.acquireAvailablePacketBundle();
        } catch (HardwareInterfaceException e) {
            log.fine("FlyEye drain after timestamp reset: " + e.getMessage());
        }
    }

    /** Last-timestamp delta (left − right) when both polarity packets are non-empty. */
    private static Long packetTimestampDeltaUs(AEMonitorInterface left, AEMonitorInterface right) {
        TimestampSample s = snapshotPolarityTimestamps(left, right);
        return s.deltaUs();
    }

    private static TimestampSample snapshotPolarityTimestamps(AEMonitorInterface left, AEMonitorInterface right) {
        try {
            PacketBundle lb = left.acquireAvailablePacketBundle();
            PacketBundle rb = right.acquireAvailablePacketBundle();
            EventPacket<?> lp = lb == null ? null : lb.getFirstPolarityPacket();
            EventPacket<?> rp = rb == null ? null : rb.getFirstPolarityPacket();
            Long lastL = (lp == null || lp.isEmpty()) ? null : (long) lp.getLastTimestamp();
            Long lastR = (rp == null || rp.isEmpty()) ? null : (long) rp.getLastTimestamp();
            return new TimestampSample(lastL, lastR);
        } catch (HardwareInterfaceException e) {
            return TimestampSample.empty();
        }
    }

    private TimestampSample waitForPolaritySample(AEMonitorInterface left, AEMonitorInterface right, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        TimestampSample last = TimestampSample.empty();
        while (System.currentTimeMillis() < deadline) {
            last = snapshotPolarityTimestamps(left, right);
            if (last.hasBoth()) {
                return last;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return last;
            }
        }
        return last;
    }

    /**
     * After a vendor reset, sample polarity twice. A slave without a cable does
     * not advance; a reversed or missing cable also shows a large dt.
     */
    CablingCheck probeCabling() {
        AEMonitorInterface left = getAemonLeft();
        AEMonitorInterface right = getAemonRight();
        if (left == null || right == null) {
            return CablingCheck.inconclusive("missing camera");
        }
        applySyncMaster(false);
        drainChildBundles(left, right);
        TimestampSample first = waitForPolaritySample(left, right, TIMESTAMP_RESET_WAIT_MS);
        try {
            Thread.sleep(CABLING_TEST_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CablingCheck.inconclusive("interrupted");
        }
        TimestampSample second = waitForPolaritySample(left, right, TIMESTAMP_RESET_WAIT_MS);
        return CablingCheck.fromSamples(first, second);
    }

    static long lastHardwareResetNanos(AEMonitorInterface aemon) {
        if (aemon instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2 fx2) {
            return fx2.getLastHardwareResetEventNanos();
        }
        if (aemon instanceof CypressFX2DVS128HardwareInterface usbIo) {
            return usbIo.getLastHardwareResetEventNanos();
        }
        return 0L;
    }

    void showTimestampResetDialog(TimestampResetResult result) {
        if (flyEye == null) {
            return;
        }
        AEViewer viewer = flyEye.getAeViewer();
        FlyEye.TimestampMaster master = flyEye.getTimestampMaster();
        boolean masterEnabled = master != FlyEye.TimestampMaster.NONE;
        CablingCheck cabling = null;
        if (masterEnabled && !SwingUtilities.isEventDispatchThread()) {
            applySyncMaster(false);
            cabling = probeCabling();
            if (cabling.status == CablingCheck.Status.OK) {
                log.info("FlyEye cabling check OK: " + cabling.detail);
            } else {
                log.warning("FlyEye cabling check " + cabling.status + ": " + cabling.detail);
            }
        }
        String msg = timestampDialogHtml(result, cabling, master);
        int type = timestampDialogType(result, cabling);
        Runnable show = () -> JOptionPane.showMessageDialog(viewer, msg, "FlyEye timestamps", type);
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            SwingUtilities.invokeLater(show);
        }
    }

    private String timestampDialogHtml(TimestampResetResult result, CablingCheck cabling,
            FlyEye.TimestampMaster master) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("Timestamp master: <b>").append(flyEye.timestampMasterLabel()).append("</b>");
        if (master == FlyEye.TimestampMaster.LEFT) {
            sb.append(" (right is slave).");
        } else if (master == FlyEye.TimestampMaster.RIGHT) {
            sb.append(" (left is slave).");
        } else {
            sb.append('.');
        }
        sb.append("<br>");
        if (result.aligned && result.deltaUs != null) {
            sb.append(String.format(
                    "Both cameras were timestamp-reset. Last packet times then differed by <b>%,d us</b> (limit 10 ms).<br>",
                    Math.abs(result.deltaUs)));
        } else if (result.aligned) {
            sb.append("Both cameras sent timestamp-reset events (no polarity yet to measure dt).<br>");
        } else {
            sb.append("Could not confirm a timestamp reset on both cameras");
            if (result.detail != null) {
                sb.append(" (").append(result.detail).append(')');
            }
            sb.append(".<br>");
        }
        if (master == FlyEye.TimestampMaster.NONE) {
            sb.append("No sync cable: clocks will drift. Use <b>Control → Zero timestamps</b> (0) after reconnecting.");
            return sb.toString();
        }
        String masterSide = master == FlyEye.TimestampMaster.LEFT ? "left" : "right";
        String slaveSide = master == FlyEye.TimestampMaster.LEFT ? "right" : "left";
        String cable = "Connect <b>" + masterSide + " OUT</b> to <b>" + slaveSide + " IN</b> and share GND.";
        if (cabling == null) {
            sb.append(cable);
            return sb.toString();
        }
        if (cabling.lastLeftUs != null || cabling.lastRightUs != null) {
            sb.append(String.format("Cabling sample: left last=%s, right last=%s",
                    formatUs(cabling.lastLeftUs), formatUs(cabling.lastRightUs)));
            if (cabling.deltaUs != null) {
                sb.append(String.format(", dt=%,d us (limit %,d us)", Math.abs(cabling.deltaUs), CABLING_SYNC_US));
            }
            sb.append(".<br>");
        }
        switch (cabling.status) {
            case OK -> sb.append("Cabling check: both cameras advancing and closely synchronized.");
            case FAIL -> {
                sb.append("Cabling check failed: ").append(cabling.detail).append(".<br>");
                sb.append(cable);
            }
            case INCONCLUSIVE -> {
                sb.append("Cabling check inconclusive: ").append(cabling.detail).append(".<br>");
                sb.append("Stimulate both cameras and use <b>FlyEye → Reset timestamps…</b>. ").append(cable);
            }
        }
        return sb.toString();
    }

    private static int timestampDialogType(TimestampResetResult result, CablingCheck cabling) {
        if (cabling != null && cabling.status == CablingCheck.Status.FAIL) {
            return JOptionPane.WARNING_MESSAGE;
        }
        if (cabling != null && cabling.status == CablingCheck.Status.OK) {
            return JOptionPane.INFORMATION_MESSAGE;
        }
        if (!result.aligned) {
            return JOptionPane.WARNING_MESSAGE;
        }
        return JOptionPane.INFORMATION_MESSAGE;
    }

    private static String formatUs(Long us) {
        return us == null ? "no events" : String.format("%,d us", us);
    }

    static final class TimestampSample {
        final Long lastLeftUs;
        final Long lastRightUs;

        TimestampSample(Long lastLeftUs, Long lastRightUs) {
            this.lastLeftUs = lastLeftUs;
            this.lastRightUs = lastRightUs;
        }

        static TimestampSample empty() {
            return new TimestampSample(null, null);
        }

        boolean hasBoth() {
            return lastLeftUs != null && lastRightUs != null;
        }

        Long deltaUs() {
            return hasBoth() ? lastLeftUs - lastRightUs : null;
        }
    }

    static final class CablingCheck {
        enum Status { OK, FAIL, INCONCLUSIVE }

        final Status status;
        final boolean leftAdvanced;
        final boolean rightAdvanced;
        final Long deltaUs;
        final Long lastLeftUs;
        final Long lastRightUs;
        final String detail;

        private CablingCheck(Status status, boolean leftAdvanced, boolean rightAdvanced,
                Long deltaUs, Long lastLeftUs, Long lastRightUs, String detail) {
            this.status = status;
            this.leftAdvanced = leftAdvanced;
            this.rightAdvanced = rightAdvanced;
            this.deltaUs = deltaUs;
            this.lastLeftUs = lastLeftUs;
            this.lastRightUs = lastRightUs;
            this.detail = detail;
        }

        static CablingCheck inconclusive(String detail) {
            return new CablingCheck(Status.INCONCLUSIVE, false, false, null, null, null, detail);
        }

        static CablingCheck fromSamples(TimestampSample first, TimestampSample second) {
            Long lastL = second.lastLeftUs != null ? second.lastLeftUs : first.lastLeftUs;
            Long lastR = second.lastRightUs != null ? second.lastRightUs : first.lastRightUs;
            Long delta = second.hasBoth() ? second.deltaUs() : first.deltaUs();
            if (!second.hasBoth()) {
                String missing;
                if (!first.hasBoth()) {
                    missing = lastL == null && lastR == null ? "no polarity events from either camera"
                            : lastL == null ? "no polarity events from left camera"
                            : "no polarity events from right camera";
                } else {
                    missing = "could not take a second polarity sample from both cameras";
                }
                return new CablingCheck(Status.INCONCLUSIVE, false, false, delta, lastL, lastR, missing);
            }
            boolean leftAdv = first.lastLeftUs != null
                    ? (second.lastLeftUs - first.lastLeftUs) >= CABLING_MIN_ADVANCE_US
                    : second.lastLeftUs >= CABLING_MIN_ADVANCE_US;
            boolean rightAdv = first.lastRightUs != null
                    ? (second.lastRightUs - first.lastRightUs) >= CABLING_MIN_ADVANCE_US
                    : second.lastRightUs >= CABLING_MIN_ADVANCE_US;
            if (!leftAdv || !rightAdv) {
                String which = !leftAdv && !rightAdv ? "left and right timestamps are not advancing"
                        : !leftAdv ? "left timestamps are not advancing"
                        : "right timestamps are not advancing";
                return new CablingCheck(Status.FAIL, leftAdv, rightAdv, delta, lastL, lastR, which);
            }
            if (delta == null || Math.abs(delta) > CABLING_SYNC_US) {
                String d = delta == null ? "could not measure dt"
                        : String.format("last times differ by %,d us (limit %,d us)", Math.abs(delta), CABLING_SYNC_US);
                return new CablingCheck(Status.FAIL, leftAdv, rightAdv, delta, lastL, lastR, d);
            }
            return new CablingCheck(Status.OK, true, true, delta, lastL, lastR,
                    String.format("dt=%d us, both advancing", delta));
        }
    }

    static final class TimestampResetResult {
        final boolean aligned;
        final boolean bothResetEvents;
        final Long deltaUs;
        final String detail;

        private TimestampResetResult(boolean aligned, boolean bothResetEvents, Long deltaUs, String detail) {
            this.aligned = aligned;
            this.bothResetEvents = bothResetEvents;
            this.deltaUs = deltaUs;
            this.detail = detail;
        }

        static TimestampResetResult aligned(long deltaUs, boolean bothResetEvents) {
            return new TimestampResetResult(true, bothResetEvents, deltaUs, "dt=" + deltaUs + " us");
        }

        static TimestampResetResult misaligned(long deltaUs, boolean bothResetEvents) {
            return new TimestampResetResult(false, bothResetEvents, deltaUs,
                    "dt=" + deltaUs + " us (limit " + TIMESTAMP_ALIGN_US + " us)");
        }

        static TimestampResetResult failed(String detail) {
            return new TimestampResetResult(false, false, null, detail);
        }

        static TimestampResetResult resetEventsOnly() {
            return new TimestampResetResult(true, true, null,
                    "both reset events, no polarity dt");
        }
    }

    /** Swap which USB device is left vs right (after swap-eyes menu). */
    public void swapSides() {
        AEMonitorInterface tmp = getAemonLeft();
        setAemonLeft(getAemonRight());
        setAemonRight(tmp);
        BiasgenHardwareInterfaceSwap();
        if (isOpen()) {
            configureSyncMaster();
        }
        setChip(flyEye);
    }

    private void BiasgenHardwareInterfaceSwap() {
        var tmp = biasgenLeft;
        biasgenLeft = biasgenRight;
        biasgenRight = tmp;
    }

    @Override
    public String getTypeName() {
        return "FlyEye";
    }

    @Override
    public PacketBundle acquireAvailablePacketBundle() throws HardwareInterfaceException {
        if (!liveAcquisitionWanted()) {
            return null;
        }
        AEMonitorInterface left = getAemonLeft();
        AEMonitorInterface right = getAemonRight();
        if (left == null || right == null) {
            return null;
        }
        if ((left instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2 l && l.isInEndpointLost())
                || (right instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2 r && r.isInEndpointLost())) {
            throw new HardwareInterfaceException("USB IN endpoint lost (unplug)");
        }
        PacketBundle leftBundle = left.acquireAvailablePacketBundle();
        PacketBundle rightBundle = right.acquireAvailablePacketBundle();
        if (leftBundle == null || rightBundle == null) {
            return null;
        }
        EventPacket<?> lp = leftBundle.getFirstPolarityPacket();
        EventPacket<?> rp = rightBundle.getFirstPolarityPacket();
        warnIfDesynced(lp, rp);
        EventPacket<FlyEyeEvent> dest = useSlot0 ? merged0 : merged1;
        PacketBundle out = useSlot0 ? bundle0 : bundle1;
        useSlot0 = !useSlot0;
        mergePolarity(dest, lp, rp);
        out.clear();
        out.add(dest);
        return out;
    }

    /** False during file playback so child DVS128 acquires cannot auto-start USB IN. */
    private boolean liveAcquisitionWanted() {
        AEViewer v = flyEye == null ? null : flyEye.getAeViewer();
        if (v != null) {
            AEViewer.PlayMode mode = v.getPlayMode();
            if (mode == AEViewer.PlayMode.PLAYBACK || mode == AEViewer.PlayMode.FILTER_INPUT) {
                return false;
            }
        }
        return isEventAcquisitionEnabled();
    }

    private void warnIfDesynced(EventPacket<?> lp, EventPacket<?> rp) {
        if (flyEye == null || !flyEye.isElectricallyTimestampSynced()) {
            hideDesyncDialog(false);
            return;
        }
        AEViewer viewer = flyEye.getAeViewer();
        if (viewer != null && viewer.getPlayMode() != AEViewer.PlayMode.LIVE) {
            hideDesyncDialog(false);
            return;
        }
        if (lp == null || rp == null) {
            return;
        }
        boolean leftEmpty = lp.isEmpty();
        boolean rightEmpty = rp.isEmpty();
        if (leftEmpty && rightEmpty) {
            return;
        }
        long now = System.currentTimeMillis();
        if (leftEmpty || rightEmpty) {
            if (oneSidedEmptySinceMs == 0L) {
                oneSidedEmptySinceMs = now;
                if (log.isLoggable(Level.FINE)) {
                    log.fine("FlyEye one-sided empty start side=" + (leftEmpty ? "left" : "right"));
                }
            }
            if (now - oneSidedEmptySinceMs < ONE_SIDED_EMPTY_MS) {
                return;
            }
            String side = leftEmpty ? "left" : "right";
            if (!loggedSlaveStopped) {
                loggedSlaveStopped = true;
                log.warning("FlyEye no polarity from " + side
                        + " camera (slave clock may have stopped; check sync cable). timestamp master="
                        + flyEye.timestampMasterLabel());
            }
            showDesyncDialog("<html>FlyEye slave may have stopped.<br>No polarity from the <b>"
                    + side + "</b> camera. Timestamp master: <b>" + flyEye.timestampMasterLabel()
                    + "</b>.<br>Reconnect master OUT to slave IN and GND.<br>"
                    + "<b>OK</b> zeros both cameras' timestamps.");
            return;
        }
        oneSidedEmptySinceMs = 0L;
        int lts = lp.getLastTimestamp();
        int rts = rp.getLastTimestamp();
        long dt = Math.abs((long) lts - rts);
        if (dt <= DESYNC_CLEAR_US) {
            lastDesyncLeftTs = lts;
            lastDesyncRightTs = rts;
            loggedSlaveStopped = false;
            hideDesyncDialog(false);
            return;
        }
        boolean leftFrozen = lastDesyncLeftTs != Integer.MIN_VALUE && lts == lastDesyncLeftTs;
        boolean rightFrozen = lastDesyncRightTs != Integer.MIN_VALUE && rts == lastDesyncRightTs;
        lastDesyncLeftTs = lts;
        lastDesyncRightTs = rts;
        if (leftFrozen || rightFrozen) {
            String side = leftFrozen && rightFrozen ? "left and right"
                    : leftFrozen ? "left" : "right";
            if (!loggedSlaveStopped) {
                loggedSlaveStopped = true;
                log.warning("FlyEye " + side + " timestamps stopped after unplugging the sync cable"
                        + " (not crystal drift). timestamp master=" + flyEye.timestampMasterLabel()
                        + " leftLast=" + lts + " rightLast=" + rts + " dt=" + String.format("%,d", dt) + " us"
                        + " left=" + describe(getAemonLeft()) + " right=" + describe(getAemonRight()));
            }
            showDesyncDialog("<html>FlyEye <b>" + side + "</b> timestamps stopped (sync cable unplugged).<br>"
                    + "This is the slave clock stopping, not crystal drift.<br>"
                    + "Timestamp master: <b>" + flyEye.timestampMasterLabel() + "</b>.<br>"
                    + "Reconnect master OUT to slave IN and GND.<br>"
                    + "<b>OK</b> zeros both cameras' timestamps.");
            return;
        }
        if (dt < DESYNC_SHOW_US) {
            // Between clear and show: USB jitter around 100 ms must not toggle the dialog.
            return;
        }
        if (now - lastDesyncLogMs >= 10_000) {
            lastDesyncLogMs = now;
            log.warning("FlyEye cameras desynced by " + String.format("%,d", dt)
                    + " us (show>" + (DESYNC_SHOW_US / 1000) + " ms, hide<" + (DESYNC_CLEAR_US / 1000)
                    + " ms); both still advancing. timestamp master="
                    + flyEye.timestampMasterLabel()
                    + " leftLast=" + lts + " rightLast=" + rts
                    + " left=" + describe(getAemonLeft()) + " right=" + describe(getAemonRight()));
        }
        showDesyncDialog("<html>FlyEye cameras are more than " + (DESYNC_WARN_US / 1000)
                + " ms apart (dt=" + String.format("%,d", dt) + " us) but both timestamps are advancing.<br>"
                + "Timestamp master: <b>" + flyEye.timestampMasterLabel() + "</b>.<br>"
                + "Check the sync cable (master OUT to slave IN and GND).<br>"
                + "<b>OK</b> zeros both cameras' timestamps.");
    }

    private void showDesyncDialog(String html) {
        Runnable show = () -> {
            if (desyncDialog != null && desyncDialog.isVisible()) {
                long t = System.currentTimeMillis();
                if (html.equals(lastDesyncHtml) || t - lastDesyncMessageMs < DESYNC_MESSAGE_UPDATE_MS) {
                    return;
                }
                lastDesyncHtml = html;
                lastDesyncMessageMs = t;
                desyncPane.setMessage(html);
                return;
            }
            if (System.currentTimeMillis() < desyncDialogSuppressedUntilMs) {
                return;
            }
            ensureDesyncDialog();
            lastDesyncHtml = html;
            lastDesyncMessageMs = System.currentTimeMillis();
            desyncPane.setMessage(html);
            desyncPane.setValue(JOptionPane.UNINITIALIZED_VALUE);
            desyncDialog.pack();
            AEViewer viewer = flyEye == null ? null : flyEye.getAeViewer();
            desyncDialog.setLocationRelativeTo(viewer);
            if (log.isLoggable(Level.FINE)) {
                log.fine("FlyEye desync dialog show");
            }
            desyncDialog.setVisible(true);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            SwingUtilities.invokeLater(show);
        }
    }

    /** @param disposeIfCreated true when closing hardware; otherwise only hide. */
    private void hideDesyncDialog(boolean disposeIfCreated) {
        Runnable hide = () -> {
            if (desyncDialog == null) {
                return;
            }
            boolean wasVisible = desyncDialog.isVisible();
            if (!wasVisible && !disposeIfCreated) {
                return;
            }
            hidingDesyncBecauseRecovered = true;
            try {
                if (wasVisible) {
                    desyncDialog.setVisible(false);
                    if (log.isLoggable(Level.FINE)) {
                        log.fine("FlyEye desync dialog hide");
                    }
                }
                lastDesyncHtml = null;
                if (disposeIfCreated) {
                    desyncDialog.dispose();
                    desyncDialog = null;
                    desyncPane = null;
                }
            } finally {
                hidingDesyncBecauseRecovered = false;
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            hide.run();
        } else {
            SwingUtilities.invokeLater(hide);
        }
    }

    private void userDismissedDesyncDialog() {
        desyncDialogSuppressedUntilMs = System.currentTimeMillis() + DESYNC_DIALOG_SUPPRESS_MS;
        if (desyncDialog != null) {
            desyncDialog.setVisible(false);
        }
    }

    private long lastResyncMs;

    /**
     * Vendor-reset both DVS128s and drop leftover packets so the next LIVE
     * packets share t=0. Suppresses the desync dialog while USB drains.
     */
    private void zeroTimestampsForResync(String reason) {
        long now = System.currentTimeMillis();
        if (now - lastResyncMs < 500L) {
            return;
        }
        lastResyncMs = now;
        log.info("FlyEye zeroing timestamps (" + reason + ")");
        hideDesyncDialog(false);
        desyncDialogSuppressedUntilMs = System.currentTimeMillis() + DESYNC_DIALOG_SUPPRESS_MS;
        loggedSlaveStopped = false;
        lastDesyncLeftTs = Integer.MIN_VALUE;
        lastDesyncRightTs = Integer.MIN_VALUE;
        oneSidedEmptySinceMs = 0L;
        resetTimestamps();
        AEMonitorInterface left = getAemonLeft();
        AEMonitorInterface right = getAemonRight();
        if (left != null && right != null) {
            drainChildBundles(left, right);
        }
    }

    private void attachPlayModeListener() {
        AEViewer viewer = flyEye == null ? null : flyEye.getAeViewer();
        if (viewer == null) {
            return;
        }
        if (playModeListener != null && playModeViewer == viewer) {
            return;
        }
        detachPlayModeListener();
        playModeListener = evt -> {
            if (!AEViewer.PlayMode.LIVE.toString().equals(String.valueOf(evt.getNewValue()))) {
                return;
            }
            if (!AEViewer.PlayMode.PLAYBACK.toString().equals(String.valueOf(evt.getOldValue()))) {
                return;
            }
            zeroTimestampsForResync("playback closed, returning LIVE");
        };
        playModeViewer = viewer;
        viewer.getSupport().addPropertyChangeListener(AEViewer.EVENT_PLAYMODE, playModeListener);
    }

    private void detachPlayModeListener() {
        if (playModeListener != null && playModeViewer != null) {
            playModeViewer.getSupport().removePropertyChangeListener(AEViewer.EVENT_PLAYMODE, playModeListener);
        }
        playModeListener = null;
        playModeViewer = null;
    }

    private void ensureDesyncDialog() {
        if (desyncDialog != null) {
            return;
        }
        AEViewer viewer = flyEye == null ? null : flyEye.getAeViewer();
        desyncPane = new JOptionPane("", JOptionPane.WARNING_MESSAGE, JOptionPane.DEFAULT_OPTION);
        desyncDialog = desyncPane.createDialog(viewer, "FlyEye timestamps");
        desyncDialog.setModal(false);
        desyncDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        desyncDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!hidingDesyncBecauseRecovered) {
                    userDismissedDesyncDialog();
                }
            }
        });
        desyncPane.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            if (!JOptionPane.VALUE_PROPERTY.equals(evt.getPropertyName())) {
                return;
            }
            Object v = evt.getNewValue();
            if (v == null || v == JOptionPane.UNINITIALIZED_VALUE) {
                return;
            }
            if (hidingDesyncBecauseRecovered) {
                return;
            }
            zeroTimestampsForResync("desync dialog OK");
        });
    }

    private void mergePolarity(EventPacket<FlyEyeEvent> dest, EventPacket<?> leftPkt, EventPacket<?> rightPkt) {
        int nL = leftPkt == null ? 0 : leftPkt.getSize();
        int nR = rightPkt == null ? 0 : rightPkt.getSize();
        if (nL == 0 && nR == 0 && log.isLoggable(Level.FINE)) {
            long now = System.currentTimeMillis();
            if (now - lastEmptyMergeLogMs > 2000) {
                lastEmptyMergeLogMs = now;
                log.fine("FlyEye merge empty nL=0 nR=0");
            }
        }
        OutputEventIterator<FlyEyeEvent> out = dest.outputIterator();
        // Independent clocks drift; merge-by-time would need unbounded hold-back.
        // Only timestamp-merge when a sync cable makes the ticks comparable.
        if (flyEye == null || !flyEye.isElectricallyTimestampSynced()) {
            appendRemap(out, leftPkt, nL, false);
            appendRemap(out, rightPkt, nR, true);
            return;
        }
        int iL = 0;
        int iR = 0;
        while (iL < nL || iR < nR) {
            boolean takeLeft;
            if (iL >= nL) {
                takeLeft = false;
            } else if (iR >= nR) {
                takeLeft = true;
            } else {
                takeLeft = leftPkt.getEvent(iL).timestamp <= rightPkt.getEvent(iR).timestamp;
            }
            if (takeLeft) {
                copyRemap(out.nextOutput(), (PolarityEvent) leftPkt.getEvent(iL++), false);
            } else {
                copyRemap(out.nextOutput(), (PolarityEvent) rightPkt.getEvent(iR++), true);
            }
        }
    }

    private void appendRemap(OutputEventIterator<FlyEyeEvent> out, EventPacket<?> pkt, int n, boolean right) {
        for (int i = 0; i < n; i++) {
            copyRemap(out.nextOutput(), (PolarityEvent) pkt.getEvent(i), right);
        }
    }

    private void copyRemap(FlyEyeEvent dest, PolarityEvent src, boolean right) {
        dest.copyFrom(src);
        dest.camera = right ? FlyEyeEvent.Camera.RIGHT : FlyEyeEvent.Camera.LEFT;
        if (src.isSpecial()) {
            return;
        }
        boolean flip = right ? flyEye.isFlipRightX() : flyEye.isFlipLeftX();
        dest.x = (short) FlyEyeGeometry.toPanoramicX(src.x, right, flip, flyEye.getOverlapPixels());
    }
}
