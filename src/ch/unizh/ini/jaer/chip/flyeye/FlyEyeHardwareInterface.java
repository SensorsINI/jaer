/*
 * FlyEyeHardwareInterface.java
 *
 * Two DVS128 USB interfaces claimed by one FlyEye AEChip. Does not open USB in
 * the chip getter; ViewLoop openAEMonitor opens this composite.
 */
package ch.unizh.ini.jaer.chip.flyeye;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.logging.Logger;

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

    @Override
    public void open() throws HardwareInterfaceException {
        super.open();
        assignEyesBySerialAndPref();
        configureSyncMaster();
        logAssignment();
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

    private void configureSyncMaster() {
        AEMonitorInterface left = getAemonLeft();
        AEMonitorInterface right = getAemonRight();
        if (left instanceof HasSyncEventOutput syncLeft) {
            syncLeft.setSyncEventEnabled(true);
        }
        if (right instanceof HasSyncEventOutput syncRight) {
            syncRight.setSyncEventEnabled(false);
        }
        log.info("FlyEye left DVS128 is timestamp master");
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
        OutputEventIterator<FlyEyeEvent> out = dest.outputIterator();
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
