/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.multicamera;

/**
 *
 * @author Gemma
 *
 * MultiDAVIS240CCameraChip.java
 *
 */

import java.util.ArrayList;
import java.util.TreeMap;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraApsDvsEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.stereopsis.MultiCameraBiasgenHardwareInterface;
import net.sf.jaer.stereopsis.MultiCameraInterface;


import eu.seebetter.ini.chips.davis.DAVIS240C;
import eu.seebetter.ini.chips.davis.Davis240Config;
import eu.seebetter.ini.chips.davis.DavisConfig;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.graphics.MulticameraDavisRenderer;
@Description("A multi Davis retina each on it's own USB interface with merged and presumably aligned fields of view")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class MultiDAVIS240CCameraChip extends DAVIS240C implements MultiCameraInterface {

    private AEChip[] cameras = new AEChip[MultiCameraApsDvsEvent.NUM_CAMERAS];
    public MulticameraDavisRenderer  MultiDavisRenderer;

    /** Creates a new instance of  */
    public MultiDAVIS240CCameraChip() {
        super();
        for (AEChip c : cameras) {
            c = new DAVIS240C();
        }
        
        setName("MultiDAVIS240CCameraChip");
        setSizeX(DAVIS240C.WIDTH_PIXELS);
        setSizeY(DAVIS240C.HEIGHT_PIXELS);

        setEventClass(MultiCameraApsDvsEvent.class);
        setDefaultPreferencesFile("biasgenSettings/Davis240b/MultiDAVIS240CCameraChip.xml");
//        MultiDavisRenderer= new MulticameraDavisRenderer(this);
//        MultiDavisRenderer.setNumCameras(MultiCameraApsDvsEvent.NUM_CAMERAS);

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
        return MultiCameraApsDvsEvent.NUM_CAMERAS*2;
    }

    @Override
    public int getNumCameras() {
        return MultiCameraApsDvsEvent.NUM_CAMERAS;
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
    public class Extractor extends DAVIS240C.DavisEventExtractor {

        public Extractor(MultiDAVIS240CCameraChip chip) {
            super(new DAVIS240C()); // they are the same type
        }

        /** extracts the meaning of the raw events and returns EventPacket containing BinocularEvent.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket(MultiCameraApsDvsEvent.class);
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
                MultiCameraApsDvsEvent e = (MultiCameraApsDvsEvent) outItr.nextOutput();
                // we need to be careful to fill in all the fields here or understand how the super of MultiCameraApsDvsEvent fills its fields
                e.address = a[i];
                e.timestamp = timestamps[i];
                e.camera = MultiCameraApsDvsEvent.getCameraFromRawAddress(a[i]);
                e.x = getXFromAddress(a[i]);
                e.y = getYFromAddress(a[i]);
                // assumes that the raw address format has polarity in msb and that 0==OFF type
                int pol = a[i] & 1;
                e.polarity = pol == 0 ? ApsDvsEvent.Polarity.Off : ApsDvsEvent.Polarity.On;
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
            // we also need to add camera info to raw events
            for (int i = 0; i < packet.getSize(); i++) {
                MultiCameraApsDvsEvent mce = (MultiCameraApsDvsEvent) packet.getEvent(i);
                EventRaw event = p.getEvent(i);
                event.address=MultiCameraApsDvsEvent.setCameraNumberToRawAddress(mce.camera, event.address);
            }
            return p;
        }
    }// extractor for 

    @Override
    public void setHardwareInterface(HardwareInterface hw) {
        if (hw != null) {
            log.warning("trying to set hardware interface to " + hw + " but hardware interface should have been constructed as a MultiCameraHardwareInterface by this MultiDAVIS240CCameraChip");
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
     * construct the interfaces and opens them, because this device depends on a particular set of interfaces.
     * <p>
     * The hardware serial number IDs are used to assign cameras.
     * @return the hardware interface for this device
     */
    @Override
    public HardwareInterface getHardwareInterface() {
        if (hardwareInterface != null) {
            return hardwareInterface;
        }
        int n = HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
        log.info(n + " interfaces found!");

        if (n == 1) {
            log.warning( " couldn't build MultiCameraHardwareInterface hardware interface because only " + n + " camera is available and more cameras are needed");
            hardwareInterface= HardwareInterfaceFactory.instance().getInterface(n);
            return hardwareInterface;
        }
        
        ArrayList<HardwareInterface> hws = new ArrayList();
        for (int i = 0; i < n; i++) {
            HardwareInterface hw = HardwareInterfaceFactory.instance().getInterface(i);
            if (hw instanceof AEMonitorInterface && hw instanceof BiasgenHardwareInterface) {
                log.info("found AEMonitorInterface && BiasgenHardwareInterface " + hw);
                hws.add(hw);
            }
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
    public class Biasgen extends Davis240Config {

        /** Creates a new instance of Biasgen for DVS128 with a given hardware interface
         *@param chip the hardware interface on this chip is used
         */
        public Biasgen(final Chip chip) {
            super(chip);
            setName("MultiDAVIS240CCameraChip");
        }
    }
}

