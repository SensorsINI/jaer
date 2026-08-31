/*
 * FlyEye.java
 *
 * Two outward-looking DVS128 cameras stitched into one panoramic AEChip.
 */
package ch.unizh.ini.jaer.chip.flyeye;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
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
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FlyEyeRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2DVS128HardwareInterface;
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
    private JCheckBoxMenuItem swapEyesMenuItem;
    private JCheckBoxMenuItem flipLeftMenuItem;
    private JCheckBoxMenuItem flipRightMenuItem;
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

    @Override
    public int getNumCellTypes() {
        return 4;
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
        }
    }

    /**
     * Raw / playback path: DVS128 decode at 128-wide, camera from stereo bit,
     * panoramic remap.
     */
    public class Extractor extends DVS128.Extractor {

        public Extractor(FlyEye chip) {
            super(new DVS128());
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
                if ((addr & (CypressFX2DVS128HardwareInterface.SYNC_EVENT_BITMASK
                        | net.sf.jaer.event.BasicEvent.SPECIAL_EVENT_BIT_MASK)) != 0) {
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
