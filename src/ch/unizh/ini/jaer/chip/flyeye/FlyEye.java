/*
 * FlyEye.java
 *
 * Two outward-looking DVS128 cameras stitched into one panoramic AEChip.
 */
package ch.unizh.ini.jaer.chip.flyeye;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.UsbDevices;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.FlyEyeEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FlyEyeRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.stereopsis.StereoChipInterface;
import net.sf.jaer.stereopsis.Stereopsis;
import ch.unizh.ini.jaer.chip.retina.DVS128;

/**
 * Panoramic pair of DVS128 cameras. Selecting this Sensor binds two unused
 * DVS128 USB interfaces to this AEViewer.
 */
@Description("Two outward-looking DVS128 cameras stitched into one panoramic chip")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@UsbDevices({})
public class FlyEye extends DVS128 implements StereoChipInterface {

    private AEChip left;
    private AEChip right;
    private int overlapPixels;
    private boolean eyesSwapped;
    private boolean flipLeftX;
    private boolean flipRightX;
    private TimestampMaster timestampMaster;
    private JCheckBoxMenuItem swapEyesMenuItem;
    private JCheckBoxMenuItem flipLeftMenuItem;
    private JCheckBoxMenuItem flipRightMenuItem;
    private JRadioButtonMenuItem timestampMasterNoneItem;
    private JRadioButtonMenuItem timestampMasterLeftItem;
    private JRadioButtonMenuItem timestampMasterRightItem;

    /** Which DVS128 drives the sync cable. {@code NONE} = both are masters (no cable). */
    public enum TimestampMaster {
        NONE, LEFT, RIGHT
    }
    /** True while {@link #bindDvs128PairIfAvailable()} is on the stack so
     * {@link DVS128#update} → {@link #getHardwareInterface()} cannot re-enter. */
    private boolean bindingPair;

    public FlyEye() {
        super();
        left = new DVS128();
        right = new DVS128();
        setName("FlyEye");
        setEventClass(FlyEyeEvent.class);
        setNumCellTypes(4);
        overlapPixels = FlyEyeGeometry.clampOverlap(
                getPrefs().getInt("overlapPixels", FlyEyeGeometry.DEFAULT_OVERLAP));
        eyesSwapped = getPrefs().getBoolean("eyesSwapped", false);
        flipLeftX = getPrefs().getBoolean("flipLeftX", false);
        flipRightX = getPrefs().getBoolean("flipRightX", false);
        timestampMaster = parseTimestampMaster(getPrefs().get("timestampMaster", TimestampMaster.NONE.name()));
        setSizeX(FlyEyeGeometry.panoramicWidth(overlapPixels));
        setSizeY(FlyEyeGeometry.NATIVE_H);
        setEventExtractor(new Extractor(this));
        FlyEyeRenderer renderer = new FlyEyeRenderer(this);
        setRenderer(renderer);
        if (getCanvas() != null && getCanvas().getDisplayMethod() != null) {
            getCanvas().getDisplayMethod().addAnnotator(renderer);
        }
    }

    /**
     * Claim two unused DVS128 cameras for this viewer. Does not open USB.
     *
     * @return true if this chip now holds a {@link FlyEyeHardwareInterface}
     */
    public boolean bindDvs128PairIfAvailable() {
        if (bindingPair) {
            return hardwareInterface instanceof FlyEyeHardwareInterface;
        }
        // Claimed pairs stay closed until ViewLoop openAEMonitor. Dropping
        // !isOpen() re-entered via DVS128.update → getHardwareInterface and
        // StackOverflowError'd (jAER 4:37:55).
        if (hardwareInterface instanceof FlyEyeHardwareInterface existing
                && !existing.isUnusableAfterUnplug()) {
            return true;
        }
        bindingPair = true;
        try {
            if (hardwareInterface instanceof FlyEyeHardwareInterface) {
                super.setHardwareInterface(null);
            }
            FlyEyeHardwareInterface pair = FlyEyeHardwareInterface.claimUnusedDvs128Pair(this);
            if (pair == null) {
                return false;
            }
            super.setHardwareInterface(pair);
            return hardwareInterface instanceof FlyEyeHardwareInterface;
        } finally {
            bindingPair = false;
        }
    }

    @Override
    public HardwareInterface getHardwareInterface() {
        // DVS128.update() calls this during construction (aeViewer still null)
        // and after every setHardwareInterface (notifyObservers). Do not claim
        // while already binding. Interface / Refresh run on the EDT: pair bind
        // is WAITING / bindLiveHardwareIfCompatible.
        if (!bindingPair && getAeViewer() != null && !SwingUtilities.isEventDispatchThread()) {
            bindDvs128PairIfAvailable();
        }
        return hardwareInterface;
    }

    @Override
    public void setHardwareInterface(HardwareInterface hw) {
        if (hw == null) {
            super.setHardwareInterface(null);
            return;
        }
        if (hw instanceof FlyEyeHardwareInterface) {
            super.setHardwareInterface(hw);
            return;
        }
        log.info("FlyEye ignoring single-device bind " + hw + "; claiming DVS128 pair");
        if (!bindDvs128PairIfAvailable()) {
            super.setHardwareInterface(null);
        }
    }

    @Override
    public AEChip getLeft() {
        return left;
    }

    @Override
    public AEChip getRight() {
        return right;
    }

    @Override
    public void setLeft(AEChip left) {
        this.left = left;
    }

    @Override
    public void setRight(AEChip right) {
        this.right = right;
    }

    @Override
    public void swapEyes() {
        AEChip tmp = getLeft();
        setLeft(getRight());
        setRight(tmp);
        eyesSwapped = !eyesSwapped;
        getPrefs().putBoolean("eyesSwapped", eyesSwapped);
        if (swapEyesMenuItem != null) {
            swapEyesMenuItem.setSelected(eyesSwapped);
        }
        if (hardwareInterface instanceof FlyEyeHardwareInterface flyHw) {
            flyHw.swapSides();
        }
    }

    public boolean isEyesSwapped() {
        return eyesSwapped;
    }

    public int getOverlapPixels() {
        return overlapPixels;
    }

    public void setOverlapPixels(int overlapPixels) {
        int ov = FlyEyeGeometry.clampOverlap(overlapPixels);
        this.overlapPixels = ov;
        getPrefs().putInt("overlapPixels", ov);
        setSizeX(FlyEyeGeometry.panoramicWidth(ov));
        if (getRenderer() != null) {
            getRenderer().ensurePixmapReadyForDisplay();
        }
    }

    public boolean isFlipLeftX() {
        return flipLeftX;
    }

    public void setFlipLeftX(boolean flipLeftX) {
        this.flipLeftX = flipLeftX;
        getPrefs().putBoolean("flipLeftX", flipLeftX);
    }

    public boolean isFlipRightX() {
        return flipRightX;
    }

    public void setFlipRightX(boolean flipRightX) {
        this.flipRightX = flipRightX;
        getPrefs().putBoolean("flipRightX", flipRightX);
    }

    public TimestampMaster getTimestampMaster() {
        return timestampMaster;
    }

    public void setTimestampMaster(TimestampMaster timestampMaster) {
        if (timestampMaster == null) {
            timestampMaster = TimestampMaster.NONE;
        }
        this.timestampMaster = timestampMaster;
        getPrefs().put("timestampMaster", timestampMaster.name());
        syncTimestampMasterMenu();
        if (hardwareInterface instanceof FlyEyeHardwareInterface flyHw && flyHw.isOpen()) {
            flyHw.configureSyncMaster();
        }
    }

    private static TimestampMaster parseTimestampMaster(String s) {
        if (s == null) {
            return TimestampMaster.NONE;
        }
        try {
            return TimestampMaster.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TimestampMaster.NONE;
        }
    }

    private void syncTimestampMasterMenu() {
        if (timestampMasterNoneItem == null) {
            return;
        }
        timestampMasterNoneItem.setSelected(timestampMaster == TimestampMaster.NONE);
        timestampMasterLeftItem.setSelected(timestampMaster == TimestampMaster.LEFT);
        timestampMasterRightItem.setSelected(timestampMaster == TimestampMaster.RIGHT);
    }

    @Override
    public int getNumCellTypes() {
        return 4;
    }

    @Override
    protected boolean includeTimestampMasterMenuItem() {
        return false;
    }

    @Override
    protected void maybeWarnWhenNotDirectSyncInterface() {
        showTimestampMasterWarning();
    }

    @Override
    protected String timestampsDisabledWarningHtml() {
        return "<html>FlyEye does not use the DVS128 “Timestamp master / Enable sync event input” checkbox.<br><br>"
                + "<b>How to set timestamp master</b><br>"
                + "Menu bar: <b>FlyEye → Timestamp master</b><br>"
                + "• <b>None (independent clocks)</b> — default, no sync cable. Then <b>Control → Zero timestamps</b> (keyboard 0) so both cameras reset.<br>"
                + "• <b>Left camera</b> or <b>Right camera</b> — that camera is hardware master. Connect its OUT pin to the other camera’s IN pin and connect GND.<br>"
                + "You can also use <b>FlyEye → Reset timestamps…</b> to confirm both clocks.";
    }

    @Override
    public void onRegistration() {
        super.onRegistration();
        if (getAeViewer() == null || dvs128Menu == null) {
            return;
        }
        if (swapEyesMenuItem == null) {
            swapEyesMenuItem = new JCheckBoxMenuItem("Swap left/right USB cameras");
            swapEyesMenuItem.setSelected(eyesSwapped);
            swapEyesMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    swapEyes();
                }
            });
            dvs128Menu.add(swapEyesMenuItem);

            flipLeftMenuItem = new JCheckBoxMenuItem("Flip left camera X");
            flipLeftMenuItem.setSelected(flipLeftX);
            flipLeftMenuItem.addActionListener(evt -> setFlipLeftX(flipLeftMenuItem.isSelected()));
            dvs128Menu.add(flipLeftMenuItem);

            flipRightMenuItem = new JCheckBoxMenuItem("Flip right camera X");
            flipRightMenuItem.setSelected(flipRightX);
            flipRightMenuItem.addActionListener(evt -> setFlipRightX(flipRightMenuItem.isSelected()));
            dvs128Menu.add(flipRightMenuItem);

            JMenuItem overlapItem = new JMenuItem("Overlap pixels…");
            overlapItem.setToolTipText("Columns treated as shared FOV (default 16)");
            overlapItem.addActionListener(evt -> {
                AEViewer v = getAeViewer();
                String s = JOptionPane.showInputDialog(v,
                        "Overlap columns (0–128)",
                        Integer.toString(overlapPixels));
                if (s == null) {
                    return;
                }
                try {
                    setOverlapPixels(Integer.parseInt(s.trim()));
                } catch (NumberFormatException ex) {
                    log.warning("bad overlapPixels: " + s);
                }
            });
            dvs128Menu.add(overlapItem);

            JMenu masterMenu = new JMenu("Timestamp master");
            masterMenu.setToolTipText("<html>None: both cameras run independent clocks (default, no sync cable).<br>"
                    + "Left/Right: that camera is hardware master; connect its OUT to the slave IN and share GND.");
            ButtonGroup masterGroup = new ButtonGroup();
            timestampMasterNoneItem = new JRadioButtonMenuItem("None (independent clocks)");
            timestampMasterLeftItem = new JRadioButtonMenuItem("Left camera (sync cable)");
            timestampMasterRightItem = new JRadioButtonMenuItem("Right camera (sync cable)");
            masterGroup.add(timestampMasterNoneItem);
            masterGroup.add(timestampMasterLeftItem);
            masterGroup.add(timestampMasterRightItem);
            timestampMasterNoneItem.addActionListener(evt -> setTimestampMaster(TimestampMaster.NONE));
            timestampMasterLeftItem.addActionListener(evt -> setTimestampMaster(TimestampMaster.LEFT));
            timestampMasterRightItem.addActionListener(evt -> setTimestampMaster(TimestampMaster.RIGHT));
            masterMenu.add(timestampMasterNoneItem);
            masterMenu.add(timestampMasterLeftItem);
            masterMenu.add(timestampMasterRightItem);
            dvs128Menu.add(masterMenu);
            syncTimestampMasterMenu();

            JMenuItem resetTsItem = new JMenuItem("Reset timestamps…");
            resetTsItem.setToolTipText("Vendor-reset both DVS128s and confirm PacketBundle times within 10 ms");
            resetTsItem.addActionListener(evt -> {
                if (!(hardwareInterface instanceof FlyEyeHardwareInterface flyHw) || !flyHw.isOpen()) {
                    log.warning("FlyEye hardware is not open");
                    return;
                }
                new Thread(() -> flyHw.showTimestampResetDialog(
                        flyHw.confirmTimestampResetBothCameras()), "FlyEye-ts-reset").start();
            });
            dvs128Menu.add(resetTsItem);
        }
    }

    /**
     * Raw / playback path: DVS128 decode at 128-wide, camera from stereo bit,
     * panoramic remap. AEDAT-4 stores panoramic {@code x}; {@link #getAddressFromCell}
     * must inverse-map that (dummy 128-wide {@code flipx} made right-eye
     * addresses negative so playback skipped them).
     */
    public class Extractor extends DVS128.Extractor {

        public Extractor(FlyEye chip) {
            super(chip);
        }

        /**
         * Pack panoramic display {@code x} into a DVS128 raw address plus stereo
         * bit. {@code right} is required in the overlap band; AEDAT-4 pack
         * infers unique-right as {@code panoX >= 128}.
         *
         * @return raw address, or -1 if out of range (AEDAT-4 skips those)
         */
        static int packPanoramicAddress(int panoX, int y, int onNotOff, boolean right,
                int overlapPixels, boolean flipX) {
            int ov = FlyEyeGeometry.clampOverlap(overlapPixels);
            int sizeX = FlyEyeGeometry.panoramicWidth(ov);
            if (panoX < 0 || panoX >= sizeX || y < 0 || y >= FlyEyeGeometry.NATIVE_H) {
                return -1;
            }
            int nativeX = FlyEyeGeometry.toNativeX(panoX, right, flipX, ov);
            if (nativeX < 0 || nativeX >= FlyEyeGeometry.NATIVE_W) {
                return -1;
            }
            // DVS128 fliptype: On (1) is raw bit 0 = 0. flipx: addrX = 127 - nativeX.
            int polBit = (onNotOff & 1) == 1 ? 0 : 1;
            int addrX = (FlyEyeGeometry.NATIVE_W - 1) - nativeX;
            int addr = (addrX << 1) | (y << 8) | polBit;
            if (right) {
                addr |= Stereopsis.MASK_RIGHT_ADDR;
            }
            return addr;
        }

        @Override
        public int getAddressFromCell(int x, int y, int type) {
            boolean right = x >= FlyEyeGeometry.NATIVE_W;
            boolean flip = right ? isFlipRightX() : isFlipLeftX();
            return packPanoramicAddress(x, y, type, right, getOverlapPixels(), flip);
        }

        @Override
        public int reconstructRawAddressFromEvent(TypedEvent e) {
            if (e instanceof FlyEyeEvent fe) {
                if (fe.isSpecial()) {
                    return fe.address;
                }
                int type = fe.polarity == PolarityEvent.Polarity.On ? 1 : 0;
                boolean right = fe.camera == FlyEyeEvent.Camera.RIGHT;
                boolean flip = right ? isFlipRightX() : isFlipLeftX();
                return packPanoramicAddress(fe.x, fe.y, type, right, getOverlapPixels(), flip);
            }
            return super.reconstructRawAddressFromEvent(e);
        }

        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null || out.getEventClass() != FlyEyeEvent.class) {
                out = new EventPacket<>(FlyEyeEvent.class);
            }
            if (in == null) {
                out.clear();
                return out;
            }
            extractPacket(in, out);
            return out;
        }

        @Override
        synchronized public void extractPacket(AEPacketRaw in, EventPacket outPkt) {
            if (in == null) {
                return;
            }
            int n = in.getNumEvents();
            outPkt.systemModificationTimeNs = in.systemModificationTimeNs;
            int skipBy = 1;
            if (isSubSamplingEnabled()) {
                while ((n / skipBy) > getSubsampleThresholdEventCount()) {
                    skipBy++;
                }
            }
            final int sxm = FlyEyeGeometry.NATIVE_W - 1;
            final int ov = getOverlapPixels();
            final short XMASK = 0xfe, XSHIFT = 1, YMASK = 0x7f00, YSHIFT = 8;
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = outPkt.outputIterator();
            for (int i = 0; i < n; i += skipBy) {
                int addr = a[i];
                FlyEyeEvent e = (FlyEyeEvent) outItr.nextOutput();
                e.address = addr;
                e.timestamp = timestamps[i];
                e.camera = Stereopsis.isRightRawAddress(addr)
                        ? FlyEyeEvent.Camera.RIGHT : FlyEyeEvent.Camera.LEFT;
                // Bit 15 is the stereo right-eye flag (same numeric value as the
                // single-camera DVS128 sync flag). Treating it as special made every
                // right-eye playback event x=-1 (DavisRenderer OOB, jAER 18:58:52).
                if ((addr & net.sf.jaer.event.BasicEvent.SPECIAL_EVENT_BIT_MASK) != 0) {
                    e.setSpecial(true);
                    e.x = -1;
                    e.y = -1;
                    e.type = -1;
                    e.polarity = PolarityEvent.Polarity.On;
                    continue;
                }
                e.setSpecial(false);
                e.type = (byte) ((1 - addr) & 1);
                e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                int nativeX = sxm - ((addr & XMASK) >>> XSHIFT);
                e.y = (short) ((addr & YMASK) >>> YSHIFT);
                boolean right = e.camera == FlyEyeEvent.Camera.RIGHT;
                boolean flip = right ? isFlipRightX() : isFlipLeftX();
                e.x = (short) FlyEyeGeometry.toPanoramicX(nativeX, right, flip, ov);
            }
        }
    }
}
