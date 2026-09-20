/*
 * FlyEyeHardwareInterface.java
 *
 * Two DVS128 USB interfaces claimed by one FlyEye AEChip. Does not open USB in
 * the chip getter; ViewLoop openAEMonitor opens this composite.
 */
package ch.unizh.ini.jaer.chip.flyeye;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.sf.jaer.aemonitor.AEMonitorInterface;
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
    /** Electrically synced cameras should stay well inside this after reset. */
    private static final int CABLING_SYNC_US = 2_000;
    private static final int CABLING_MIN_ADVANCE_US = 1;

    @Override
    public void open() throws HardwareInterfaceException {
        super.open();
        assignEyesBySerialAndPref();
        configureSyncMaster();
        logAssignment();
        showTimestampResetDialog(confirmTimestampResetBothCameras());
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
        AEMonitorInterface left = getAemonLeft();
        AEMonitorInterface right = getAemonRight();
        FlyEye.TimestampMaster master = flyEye == null ? FlyEye.TimestampMaster.NONE
                : flyEye.getTimestampMaster();
        boolean leftMaster = master != FlyEye.TimestampMaster.RIGHT;
        boolean rightMaster = master != FlyEye.TimestampMaster.LEFT;
        if (left instanceof HasSyncEventOutput syncLeft) {
            syncLeft.setSyncEventEnabled(leftMaster);
        }
        if (right instanceof HasSyncEventOutput syncRight) {
            syncRight.setSyncEventEnabled(rightMaster);
        }
        if (master == FlyEye.TimestampMaster.NONE) {
            log.info("FlyEye both DVS128s are timestamp masters (no sync cable)");
        } else {
            log.info("FlyEye " + (master == FlyEye.TimestampMaster.LEFT ? "left" : "right")
                    + " DVS128 is timestamp master (sync cable)");
        }
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
            drainChildBundles(left, right);
            long deadline = System.currentTimeMillis() + TIMESTAMP_RESET_WAIT_MS;
            boolean bothEvents = false;
            Long deltaUs = null;
            while (System.currentTimeMillis() < deadline) {
                bothEvents = lastHardwareResetNanos(left) > t0L
                        && lastHardwareResetNanos(right) > t0R;
                deltaUs = packetTimestampDeltaUs(left, right);
                if (deltaUs != null && Math.abs(deltaUs) <= TIMESTAMP_ALIGN_US) {
                    log.info("FlyEye timestamp reset confirmed: Δt=" + deltaUs
                            + " µs (attempt " + attempt + "/" + TIMESTAMP_RESET_TRIES
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
                        + "); no polarity packets yet to measure Δt");
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
     * not advance; a reversed or missing cable also shows a large Δt.
     */
    CablingCheck probeCabling() {
        AEMonitorInterface left = getAemonLeft();
        AEMonitorInterface right = getAemonRight();
        if (left == null || right == null) {
            return CablingCheck.inconclusive("missing camera");
        }
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
                    "Both cameras were timestamp-reset. Last packet times then differed by <b>%,d µs</b> (limit 10 ms).<br>",
                    Math.abs(result.deltaUs)));
        } else if (result.aligned) {
            sb.append("Both cameras sent timestamp-reset events (no polarity yet to measure Δt).<br>");
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
                sb.append(String.format(", Δt=%,d µs (limit %,d µs)", Math.abs(cabling.deltaUs), CABLING_SYNC_US));
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
        return us == null ? "no events" : String.format("%,d µs", us);
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
                String d = delta == null ? "could not measure Δt"
                        : String.format("last times differ by %,d µs (limit %,d µs)", Math.abs(delta), CABLING_SYNC_US);
                return new CablingCheck(Status.FAIL, leftAdv, rightAdv, delta, lastL, lastR, d);
            }
            return new CablingCheck(Status.OK, true, true, delta, lastL, lastR,
                    String.format("Δt=%d µs, both advancing", delta));
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
            return new TimestampResetResult(true, bothResetEvents, deltaUs, "Δt=" + deltaUs + " µs");
        }

        static TimestampResetResult misaligned(long deltaUs, boolean bothResetEvents) {
            return new TimestampResetResult(false, bothResetEvents, deltaUs,
                    "Δt=" + deltaUs + " µs (limit " + TIMESTAMP_ALIGN_US + " µs)");
        }

        static TimestampResetResult failed(String detail) {
            return new TimestampResetResult(false, false, null, detail);
        }

        static TimestampResetResult resetEventsOnly() {
            return new TimestampResetResult(true, true, null,
                    "both reset events, no polarity Δt");
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
        EventPacket<FlyEyeEvent> dest = useSlot0 ? merged0 : merged1;
        PacketBundle out = useSlot0 ? bundle0 : bundle1;
        useSlot0 = !useSlot0;
        mergePolarity(dest, lp, rp);
        out.clear();
        out.add(dest);
        return out;
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
