/*
 * Tmpdiff128StereoPair.java
 *
 * Created on March 18, 2006, 2:11 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright March 18, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package ch.unizh.ini.jaer.chip.stereopsis;

import java.util.ArrayList;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.BinocularDVSRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.stereopsis.StereoBiasgenHardwareInterface;
import net.sf.jaer.stereopsis.StereoChipInterface;
import net.sf.jaer.stereopsis.Stereopsis;
import ch.unizh.ini.jaer.chip.retina.DVS128;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2DVS128HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2DVS128HardwareInterfaceInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasSyncEventOutput;
import net.sf.jaer.util.HexString;

/**
 * A stereo pair of Tmpdiff128 retinas each with its own separate but
 * time-synchronized hardware interface. Differs from the usual AEChip object in
 * that it also overrides #getHardwareInterface and #setHardwareInterface to
 * supply StereoHardwareInterface which is a pair of Tmpdiff128 hardware
 * interfaces.
 *
 * @author tobi
 * @see net.sf.jaer.stereopsis.StereoHardwareInterface
 * @see net.sf.jaer.stereopsis.StereoBiasgenHardwareInterface
 */
@Description("A stereo pair of DVS128 cameras each with its own USB interface")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DVS128StereoPair extends DVS128 implements StereoChipInterface {

    private AEChip left, right;
    private JCheckBoxMenuItem swapEyesMenuItem = null;
    private boolean eyesSwapped = getPrefs().getBoolean("eyesSwapped", false);

    /**
     * Creates a new instance of Tmpdiff128StereoPair
     */
    public DVS128StereoPair() {
        super();
        left = new DVS128();
        right = new DVS128();

        setEventClass(BinocularEvent.class);
        setRenderer(new BinocularDVSRenderer(this));
//        setCanvas(new RetinaCanvas(this)); // we make this canvas so that the sizes of the chip are correctly set
        setEventExtractor(new Extractor(this));
        setBiasgen(new Biasgen(this));
        setLeft(left);
        setRight(right);
        if (eyesSwapped) {
            swapEyes();
        }

//        ArrayList<DisplayMethod> ms=getCanvas().getDisplayMethods();
//        DisplayMethod rgbaDm=null;
//        for(DisplayMethod m:ms){
//            if(m instanceof ChipRendererDisplayMethodRGBA) rgbaDm=m;
//        }
//        if(rgbaDm!=null) getCanvas().removeDisplayMethod(rgbaDm);
//        DisplayMethod m = new ChipRendererDisplayMethod(this.getCanvas()); // remove method that is incompatible with renderer
//        getCanvas().addDisplayMethod(m);
//        getCanvas().setDisplayMethod(m);
//        getFilterChain().append(new StereoTranslateRotate(this));
//        getFilterChain().append(new StereoVergenceFilter(this));
//        getFilterChain().append(new GlobalDisparityFilter(this));
//        getFilterChain().append(new GlobalDisparityFilter2(this));
//        getFilterChain().append(new DisparityFilter(this));
//        getFilterChain().append(new StereoClusterTracker(this));
//        getFilterChain().append(new Batter(this));
//        getRealTimeFilterChain().append(new Batter(this));
//        if(filterFrame!=null) filterFrame.dispose();
//        filterFrame=new FilterFrame(this);
    }

    @Override
    public void setAeViewer(AEViewer aeViewer) {
        super.setAeViewer(aeViewer);
        aeViewer.setLogFilteredEventsEnabled(false); // not supported for binocular reconstruction yet TODO
    }

    public AEChip getLeft() {
        return left;
    }

    public AEChip getRight() {
        return right;
    }

    public void setLeft(AEChip left) {
        this.left = left;
    }

    public void setRight(AEChip right) {
        this.right = right;
    }

    /**
     * swaps the left and right hardware channels. This method can be used if
     * the hardware interfaces are incorrectly assigned.
     */
    public void swapEyes() {
        AEChip tmp = getLeft();
        setLeft(getRight());
        setRight(tmp);
    }

    @Override
    public void onRegistration() {
        super.onRegistration();
        if (getAeViewer() == null) {
            return;
        }
        if (swapEyesMenuItem == null) {
            swapEyesMenuItem = new JCheckBoxMenuItem("Swap left/right eye USB input");
            swapEyesMenuItem.setToolTipText("<html>swaps usb inputs for left/right eye");

            swapEyesMenuItem.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent evt) {
                    swapEyes();
                    eyesSwapped = !eyesSwapped;
                    getPrefs().putBoolean("eyesSwapped", eyesSwapped);
                }
            });
            dvs128Menu.add(swapEyesMenuItem);
        }

    }

    @Override
    public int getNumCellTypes() {
        return 4;
    }

    /**
     * the event extractor for the stereo chip pair. It extracts from each event
     * the x,y,type of the event and in addition, it adds getNumCellTypes to
     * each type to signal a right event (as opposed to a left event)
     */
    public class Extractor extends DVS128.Extractor {

        public Extractor(DVS128StereoPair chip) {
            super(new DVS128()); // they are the same type
        }

        /**
         * extracts the meaning of the raw events and returns EventPacket
         * containing BinocularEvent.
         *
         * @param in the raw events, can be null
         * @return out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket(BinocularEvent.class);
            }
            if (in == null) {
                return out;
            }
            int n = in.getNumEvents(); //addresses.length;

            int skipBy = 1;
            if (isSubSamplingEnabled()) {
                while (n / skipBy > getSubsampleThresholdEventCount()) {
                    skipBy++;
                }
            }
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for (int i = 0; i < n; i += skipBy) { // bug here
                BinocularEvent e = (BinocularEvent) outItr.nextOutput();
                e.address = a[i];
                e.timestamp = timestamps[i];
                e.x = getXFromAddress(a[i]);
                e.y = getYFromAddress(a[i]);
                e.type = getTypeFromAddress(a[i]);
                e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                e.eye = Stereopsis.isRightRawAddress(a[i]) ? BinocularEvent.Eye.RIGHT : BinocularEvent.Eye.LEFT;
            }
            return out;
        }
//        /** @return the type of the event.
//         */
//        public byte getTypeFromAddress(short addr){
//            byte type=super.getTypeFromAddress(addr);
//            if(Stereopsis.isRightRawAddress(addr)) {
////                System.out.println("type right type");
//                type=Stereopsis.setRightType(type);
//            }
//            return type;
//        }

        /**
         * Reconstructs the raw packet after event filtering to include the
         * binocular information
         *
         * @param packet the filtered packet
         * @return the reconstructed packet
         */
        @Override
        public AEPacketRaw reconstructRawPacket(EventPacket packet) {
            AEPacketRaw p = super.reconstructRawPacket(packet);
            // we also need to append binocularity (eye) to raw events
            for (int i = 0; i < packet.getSize(); i++) {
                BinocularEvent be = (BinocularEvent) packet.getEvent(i);
                if (be.eye == BinocularEvent.Eye.RIGHT) {
                    EventRaw event = p.getEvent(i);
                    event.address &= Stereopsis.MASK_RIGHT_ADDR;
                }
            }
            return p;
        }
    }

    @Override
    public void setHardwareInterface(HardwareInterface hw) {
        if (hw != null) {
            log.warning("trying to set hardware interface to " + hw + " but hardware interface is built as StereoHardwareInterface by this device");
        }
        if (hw != null && hw.isOpen()) {
            log.info("closing hw interface");
            hw.close();
        }
        super.setHardwareInterface(hw);
    }
    boolean deviceMissingWarningLogged = false;

    /**
     * Builds and returns a StereoHardwareInterface for this stereo pair of
     * devices. Unlike other chip objects, this one actually invokes the
     * HardwareInterfaceFactory to construct the interfaces and opens them,
     * because this device depends on a particular pair of interfaces.
     * <p>
     * The hardware serial number IDs are used to assign left and right retinas.
     *
     * @return the hardware interface for this device
     */
    @Override
    public HardwareInterface getHardwareInterface() {
        if (hardwareInterface != null) {
            return hardwareInterface;
        }
        int n = HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
        if (n < 2) {
            if (deviceMissingWarningLogged = false) {
                log.warning("couldn't build AEStereoRetinaPair hardware interface because only " + n + " are available");
                deviceMissingWarningLogged = true;
            }
            return null;
        }
        if (n > 2) {
            log.info(n + " interfaces, searching them to find DVS128 interfaces");
        }

        ArrayList<HardwareInterface> hws = new ArrayList();
        for (int i = 0; i < n; i++) {
            HardwareInterface hw = HardwareInterfaceFactory.instance().getInterface(i);
            if (hw instanceof AEMonitorInterface && (hw instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2DVS128HardwareInterface
                    || hw instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2LibUsbDVS128HardwareInterface)) {
                log.info("found AEMonitorInterface && BiasgenHardwareInterface " + hw);
                hws.add(hw);
            }
        }

        if (hws.size() < 2) { //if ( hws.size() != 2 ){
            log.warning("could not find 2 interfaces which are suitable candidates for a stereo pair " + hws.size());
            return null;
        }

        HardwareInterface hw0 = hws.get(0);
        HardwareInterface hw1 = hws.get(1);
        try {
            hw0.open();
            USBInterface usb0 = (USBInterface) hw0;
            String[] sa1 = usb0.getStringDescriptors();

            hw1.open();
            USBInterface usb1 = (USBInterface) hw1;
            String[] sa2 = usb1.getStringDescriptors();

            if (sa1.length < 3 || sa2.length < 3) {
                log.warning("one or both interfaces has no serial number, cannot guarentee assignment of left/right eyes");
            } else {
                String id0 = sa1[2];
                String id1 = sa2[2];

                if (id0.compareTo(id1) > 0) {
                    HardwareInterface tmp = hw0;
                    hw0 = hw1;
                    hw1 = tmp;
                    String ts = id0;
                    id0 = id1;
                    id1 = ts;
                }
                log.info(String.format("Assigned left to serial number %s, right to serial number %s", id0, id1));
            }

        } catch (Exception ex) {
            log.warning("enumerating stereo pair: " + ex.toString());
        }
        try {
            hardwareInterface = new StereoBiasgenHardwareInterface((AEMonitorInterface) hw0, (AEMonitorInterface) hw1);
            ((StereoBiasgenHardwareInterface) hardwareInterface).setChip(this);
            CypressFX2DVS128HardwareInterfaceInterface hwi = (CypressFX2DVS128HardwareInterfaceInterface) hw0;
            hwi.setSyncEventEnabled(true);
            hwi = (CypressFX2DVS128HardwareInterfaceInterface) hw1;
            hwi.setSyncEventEnabled(false);
            log.info("Left DVS is set to timestamp master");
            hardwareInterface.close(); // will be opened later on by user

        } catch (ClassCastException e) {
            log.warning("couldn't build correct stereo hardware interface: " + e.getMessage());
            return null;
        }
        deviceMissingWarningLogged = false;
        return hardwareInterface;
    }

    /**
     * A paired biasgen for this stereo combination of Tmpdiff128. The biases
     * are simultaneously controlled.
     *
     * @author tobi
     */
    public class Biasgen extends DVS128.Biasgen {

        /**
         * Creates a new instance of Biasgen for Tmpdiff128 with a given
         * hardware interface
         *
         * @param chip the hardware interface on this chip is used
         */
        public Biasgen(net.sf.jaer.chip.Chip chip) {
            super(chip);
            setName("DVS128StereoPair");
        }
    }
}
