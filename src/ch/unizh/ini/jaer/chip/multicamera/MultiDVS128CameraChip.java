/*
 * MultiDVS128CameraChip.java
 *
 * Created on March 18, 2006, 2:11 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright March 18, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package ch.unizh.ini.jaer.chip.multicamera;

import java.util.ArrayList;
import java.util.TreeMap;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.BinocularDVSRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.stereopsis.MultiCameraBiasgenHardwareInterface;
import net.sf.jaer.stereopsis.MultiCameraInterface;

/**
 * Multiple DVS128 retinas each with its own separate but time-synchronized hardware interface.
 * Differs from the usual AEChip object in that it also overrides #getHardwareInterface and #setHardwareInterface
to supply MultiCameraInterface which are multiple DVS128 hardware interfaces.
 * @author tobi
 * @see net.sf.jaer.stereopsis.MultiCameraInterface
 * @see net.sf.jaer.stereopsis.MultiCameraHardwareInterface
 */
import net.sf.jaer.graphics.TwoCamera3DDisplayMethod;

import ch.unizh.ini.jaer.chip.retina.DVS128;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.stereopsis.MultiCameraHardwareInterface;
@Description("A multi DVS128 retina (DVS128) each on it's own USB interface with merged and presumably aligned fields of view")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MultiDVS128CameraChip extends DVS128 implements MultiCameraInterface {
    public int NUM_CAMERAS=MultiCameraHardwareInterface.NUM_CAMERAS;
    private AEChip[] cameras = new AEChip[NUM_CAMERAS];

    /** Creates a new instance of MultiDVS128CameraChip */
    public MultiDVS128CameraChip() {
        super();
        getCanvas().addDisplayMethod(new TwoCamera3DDisplayMethod(getCanvas()));

        for (AEChip c : cameras) {
            c = new DVS128();
        }
        
        setName("MultiDVS128CameraChip");

        setEventClass(BinocularEvent.class);
        setRenderer(new BinocularDVSRenderer(this));
//        setCanvas(new RetinaCanvas(this)); // we make this canvas so that the sizes of the chip are correctly set
        setEventExtractor(new Extractor(this));
        setBiasgen(new Biasgen(this));
    }

    @Override
    public void setAeViewer(AEViewer aeViewer) {
        super.setAeViewer(aeViewer);
        aeViewer.setLogFilteredEventsEnabled(false); // not supported for binocular reconstruction yet TODO
    }

    public AEChip getCamera(int i) {
        return cameras[i];
    }

    @Override
    public int getNumCellTypes() {
        return MultiCameraEvent.NUM_CAMERAS * 2;
    }

    @Override
    public int getNumCameras() {
        return MultiCameraEvent.NUM_CAMERAS;
    }

    @Override
    public void setCamera(int i, AEChip chip) {
        cameras[i] = chip;
    }

    /** Changes order of cameras according to list in permutation (which is not checked for uniqueness or bounds).
     *
     * @param permutation  list of destination indices for elements of cameras.
     */
    @Override
    public void permuteCameras(int[] permutation) {
        AEChip[] tmp = new AEChip[permutation.length];
        System.arraycopy(cameras, 0, tmp, 0, permutation.length);

        for (int i = 0; i < permutation.length; i++) {
            cameras[i] = tmp[permutation[i]];
        }
    }

    /** the event extractor for the multi chip.
     * It extracts from each event the x,y,type of the event and in addition,
     * it adds getNumCellTypes to each type to signal
     * a right event (as opposed to a left event)
     */
    public class Extractor extends DVS128.Extractor {

        public Extractor(MultiDVS128CameraChip chip) {
            super(new DVS128()); // they are the same type
        }

        /** extracts the meaning of the raw events and returns EventPacket containing BinocularEvent.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket(MultiCameraEvent.class);
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
                MultiCameraEvent e = (MultiCameraEvent) outItr.nextOutput();
                // we need to be careful to fill in all the fields here or understand how the super of MultiCameraEvent fills its fields
                e.address = a[i];
                e.timestamp = timestamps[i];
                e.camera = MultiCameraEvent.getCameraFromRawAddress(a[i]);
                e.x = getXFromAddress(a[i]);
                e.y = getYFromAddress(a[i]);
                // assumes that the raw address format has polarity in msb and that 0==OFF type
                int pol = a[i] & 1;
                e.polarity = pol == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                // combines polarity with camera to assign 2*NUM_CAMERA types
                e.type = (byte) (2 * e.camera + pol); // assign e.type here so that superclasses don't get fooled by using default type of event for polarity event
            }
            return out;
        }

        /** Reconstructs the raw packet after event filtering to include the binocular information
        @param packet the filtered packet
        @return the reconstructed packet
         */
        @Override
        public AEPacketRaw reconstructRawPacket(EventPacket packet) {
            AEPacketRaw p = super.reconstructRawPacket(packet);
            // we also need to appendCopy camera info to raw events
            for (int i = 0; i < packet.getSize(); i++) {
                MultiCameraEvent mce = (MultiCameraEvent) packet.getEvent(i);
                EventRaw event = p.getEvent(i);
                event.address=MultiCameraEvent.setCameraNumberToRawAddress(mce.camera, event.address);
            }
            return p;
        }
    }// extractor for multidvs128camerachip

    @Override
    public void setHardwareInterface(HardwareInterface hw) {
        if (hw != null) {
            log.warning("trying to set hardware interface to " + hw + " but hardware interface should have been constructed as a MultiCameraHardwareInterface by this MultiDVS128CameraChip");
        }
        if (hw != null && hw.isOpen()) {
            log.info("closing hw interface");
            hw.close();
        }
        super.setHardwareInterface(hw);
    }
    boolean deviceMissingWarningLogged = false;

    /**Builds and returns a hardware interface for this multi camera device.
     * Unlike other chip objects, this one actually invokes the HardwareInterfaceFactory to
     * construct the interfaces and opens them, because this device depends on a particular pair of interfaces.
     * <p>
     * The hardware serial number IDs are used to assign left and right retinas.
     * @return the hardware interface for this device
     */
    @Override
    public HardwareInterface getHardwareInterface() {
        if (hardwareInterface != null) {
            return hardwareInterface;
        }
        int n = HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
        if (n < MultiCameraEvent.NUM_CAMERAS) {
            if (deviceMissingWarningLogged = false) {
                log.warning("couldn't build MultiCameraHardwareInterface hardware interface because only " + n + " are available and " + MultiCameraEvent.NUM_CAMERAS + " are needed");
                deviceMissingWarningLogged = true;
            }
            return null;
        }
        if (n > MultiCameraEvent.NUM_CAMERAS) {
            log.info(n + " interfaces, searching them to find DVS128 interfaces");
        }

        ArrayList<HardwareInterface> hws = new ArrayList();
        for (int i = 0; i < n; i++) {
            HardwareInterface hw = HardwareInterfaceFactory.instance().getInterface(i);
            if (hw instanceof AEMonitorInterface && hw instanceof BiasgenHardwareInterface) {
                log.info("found AEMonitorInterface && BiasgenHardwareInterface " + hw);
                hws.add(hw);
            }
        }

        if (hws.size() < MultiCameraEvent.NUM_CAMERAS) {
            log.warning("could not find " + MultiCameraEvent.NUM_CAMERAS + " interfaces which are suitable candidates for a multiple camera arrangement " + hws.size());
            return null;
        }

        // TODO fix assignment of cameras according to serial number order

        // make treemap (sorted map) of string serial numbers of cameras mapped to interfaces
        TreeMap<String, AEMonitorInterface> map = new TreeMap();
        for (HardwareInterface hw : hws) {
            try {
                hw.open();
                USBInterface usb0 = (USBInterface) hw;
                String[] sa = usb0.getStringDescriptors();

                if (sa.length < 3) {
                    log.warning("interface " + hw.toString() + " has no serial number, cannot guarentee assignment of cameras");
                } else {
                    map.put(sa[2], (AEMonitorInterface) hw);
                }
            } catch (Exception ex) {
                log.warning("enumerating multiple cameras: " + ex.toString());
            }

        }
        try {
            Object[] oa=map.values().toArray();
            AEMonitorInterface[] aemons=new AEMonitorInterface[oa.length];
            int ind=0;
            for(Object o:oa){
                aemons[ind++]=(AEMonitorInterface)o;
            }
            hardwareInterface = new MultiCameraBiasgenHardwareInterface(aemons);
            ((MultiCameraBiasgenHardwareInterface) hardwareInterface).setChip(this);
            hardwareInterface.close(); // will be opened later on by user
        } catch (Exception e) {
            log.warning("couldn't build correct multi camera hardware interface: " + e.getMessage());
            return null;
        }
        deviceMissingWarningLogged = false;
        return hardwareInterface;
    }

    /**
     * A biasgen for this multicamera combination of DVS128. The biases are simultaneously controlled.
     * @author tobi
     */
    public class Biasgen extends DVS128.Biasgen {

        /** Creates a new instance of Biasgen for DVS128 with a given hardware interface
         *@param chip the hardware interface on this chip is used
         */
        public Biasgen(net.sf.jaer.chip.Chip chip) {
            super(chip);
            setName("MultiDVS128CameraChip");
        }
    }
}
