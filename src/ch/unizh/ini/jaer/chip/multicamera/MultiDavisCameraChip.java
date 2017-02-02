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

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DAVIS240C;
import eu.seebetter.ini.chips.davis.Davis240Config;
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
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.stereopsis.MultiCameraBiasgenHardwareInterface;
import net.sf.jaer.stereopsis.MultiCameraInterface;


import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import eu.seebetter.ini.chips.davis.DavisConfig;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.JAERViewer;
import net.sf.jaer.aemonitor.AEPacket;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.BinocularDVSRenderer;
import net.sf.jaer.graphics.TwoCamera3DDisplayMethod;
//import net.sf.jaer.graphics.MultiViewMultiCamera;
import net.sf.jaer.stereopsis.MultiCameraHardwareInterface;
import static net.sf.jaer.stereopsis.MultiCameraHardwareInterface.getNumberOfCameraChip;

@Description("A multi Davis retina each on it's own USB interface with merged and presumably aligned fields of view")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
abstract public class MultiDavisCameraChip extends DavisBaseCamera implements MultiCameraInterface {
    public int NUM_CAMERAS=MultiCameraHardwareInterface.NUM_CAMERAS; 
    public int NUM_VIEWERS;

    private AEChip chip= new AEChip();
    private AEChip mainChip= new AEChip();
    public AEChip[] cameras= new AEChip[NUM_CAMERAS];
    public JAERViewer JAERV;
    public AEViewer mainAEV;
    public AEViewer[] camerasAEVs= new AEViewer[NUM_CAMERAS];
    public ArrayList<HardwareInterface> hws = new ArrayList();
    int nHW = HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
    private int sx;
    private int sy;

    private JMenu multiCameraMenu = null;

//    public MulticameraDavisRenderer  MultiDavisRenderer;

    /** Creates a new instance of  */
    public MultiDavisCameraChip() {
        
        super();

        setName("MultiDavisCameraChip");
        setEventClass(MultiCameraApsDvsEvent.class);
        setEventExtractor(new Extractor(this));
        
        getCanvas().addDisplayMethod(new TwoCamera3DDisplayMethod(getCanvas()));
//        getCanvas().addDisplayMethod(new MultiViewMultiCamera(getCanvas()));
  
    }
    
    public void setCameraChip(AEChip chip){
        for (int i=0; i<NUM_CAMERAS; i++) {
            cameras[i] = chip;
        }
        AEViewer.DEFAULT_CHIP_CLASS=chip.getName();
        this.chip=chip;
        sx=chip.getSizeX();
        this.setSizeX(sx);
        sy=chip.getSizeY();
        this.setSizeY(sy);
    } 
    
    public AEChip getChipType(){
        AEChip chipType=this.chip;
        return chipType;
    }
    
    public AEChip getMultiChip(){
        mainChip=this;
        return mainChip;
    } 
    
    @Override
    public int getNumCellTypes() {
        return NUM_CAMERAS*2;
    }

    @Override
    public int getNumCameras() {
        return NUM_CAMERAS;
    }
    
    @Override
    public void setCamera(int i, AEChip chip) {
        cameras[i] = chip;
    }
    
    @Override
    public AEChip getCamera(int i) {
        return cameras[i];
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
    public class Extractor extends DavisBaseCamera.DavisEventExtractor {

        public Extractor(MultiDavisCameraChip chip) {
            super(new DavisBaseCamera() {}); // they are the same type
        }

        /** extracts the meaning of the raw events and returns EventPacket containing BinocularEvent.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            final int sx = getChipType().getSizeX()-1;
            final int sy = getChipType().getSizeY()-1;
            if (out == null) {
                out = new EventPacket(MultiCameraApsDvsEvent.class);
            }else {
                out.clear();
            }
            if (in == null) {
                return out;
            }

            OutputEventIterator outItr = out.outputIterator();
            DavisEventExtractor davisExtractor = new DavisEventExtractor((DavisBaseCamera) chip);
            EventPacket davisExtractedPacket = davisExtractor.extractPacket(in);
            
            int n =davisExtractedPacket.getSize();
            
            int[] a = in.getAddresses();

            for (int i = 0; i < n; i++) { 
                
                ApsDvsEvent davisExtractedEvent= (ApsDvsEvent) davisExtractedPacket.getEvent(i);
                MultiCameraApsDvsEvent e= (MultiCameraApsDvsEvent) outItr.nextOutput();
                e.copyFrom(davisExtractedEvent);
                int address=e.address;
                //if DVS
                if (e.isDVSEvent() ){
                    e.camera = MultiCameraApsDvsEvent.getCameraFromRawAddressDVS(address);
//                    System.out.println("DVS? "+ e.isDVSEvent()+" camera: " +e.camera+" x: "+ e.x+" y: "+e.y);

                }else if (e.isApsData()){
                    e.camera = MultiCameraApsDvsEvent.getCameraFromRawAddressAPS(address);
//                    System.out.println("DVS? "+ e.isDVSEvent()+" camera: " +e.camera+" x: "+ e.x+" y: "+e.y);
//                    e.setFilteredOut(true);
                }   
            }
            return out;

//if (out == null) {
//                out = new EventPacket(MultiCameraApsDvsEvent.class);
//            }
//            if (in == null) {
//                return out;
//            }
//            int n = in.getNumEvents(); //addresses.length;
//
//            int skipBy = 1;
//            if (isSubSamplingEnabled()) {
//                while (n / skipBy > getSubsampleThresholdEventCount()) {
//                    skipBy++;
//                }
//            }
//            int[] a = in.getAddresses();
//            int[] timestamps = in.getTimestamps();
//            OutputEventIterator outItr = out.outputIterator();
//            
//            for (int i = 0; i < n; i += skipBy) { // bug here
//                MultiCameraApsDvsEvent e = (MultiCameraApsDvsEvent) outItr.nextOutput();
//                // we need to be careful to fill in all the fields here or understand how the super of MultiCameraApsDvsEvent fills its fields
//                if (e.isDVSfromRawAddress(a[i])) {
//                    e.setReadoutType(MultiCameraApsDvsEvent.ReadoutType.DVS);
//                    e.setSpecial(true);
//                    e.address = a[i];
//                    e.timestamp = timestamps[i]; 
//                    e.camera = MultiCameraApsDvsEvent.getCameraFromRawAddressDVS(a[i]);
//                    e.polarity = (a[i] & DavisChip.POLMASK) == DavisChip.POLMASK ? ApsDvsEvent.Polarity.On : ApsDvsEvent.Polarity.Off;
//                    e.type = (byte) ((a[i] & DavisChip.POLMASK) == DavisChip.POLMASK ? 1 : 0);
//                    e.x = (short) (sx - ((a[i] & DavisChip.XMASK) >>> DavisChip.XSHIFT));
//                    e.y = (short) ((a[i] & DavisChip.YMASK) >>> DavisChip.YSHIFT);
//                    
////                    System.out.println(e.timestamp+ "  camera: "+ e.camera + "  x: "+ e.x + "  y: "+e.y);
//                        
//                    if (e.camera>NUM_CAMERAS || e.camera<0){
//                        log.warning("The camera's number read from the address is wrong!");
//                    }
//                    if (e.x>sx || e.x<0 || e.y<0 || e.y>sy){
//                        log.warning("Out of borders");
//                    }
//                if (!e.isDVSfromRawAddress(a[i])){
//                    e.setFilteredOut(true);
//                    }
//                }
//            }
//            return out;
        }

        /** Reconstructs the raw packet after event filtering to include the binocular information
        @param packet the filtered packet
        @return the reconstructed packet
         */
        @Override
        public AEPacketRaw reconstructRawPacket(EventPacket oldPacket) {
            AEPacketRaw newPacket = super.reconstructRawPacket(oldPacket);
            int n=oldPacket.getSize();
            // we also need to add camera info to raw events
            for (int i = 0; i < n; i++) {
                MultiCameraApsDvsEvent mce = (MultiCameraApsDvsEvent) oldPacket.getEvent(i);
                EventRaw event = newPacket.getEvent(i);
                int eventAddress=event.address;
                int eventCamera=mce.camera;
                if (mce.isDVSEvent()){
                    event.address=MultiCameraApsDvsEvent.setCameraNumberToRawAddressDVS(eventCamera, eventAddress);
                }
                if (mce.isApsData()){
                    event.address=MultiCameraApsDvsEvent.setCameraNumberToRawAddressAPS(mce.camera, event.address);
                }
            }
            return newPacket;
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
        
        log.info(nHW + " interfaces found!");
        if (nHW <1) {
            log.warning( " couldn't build MultiCameraHardwareInterface hardware interface because only " + nHW + " camera is available and at least 2 cameras are needed");
            hardwareInterface= HardwareInterfaceFactory.instance().getInterface(nHW);
            return hardwareInterface;
        }
        
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
//            hardwareInterface.close(); // will be opened later on by user

        } catch (Exception e) {
            log.warning("couldn't build correct multi camera hardware interface: " + e.getMessage());
            return null;
        }
        deviceMissingWarningLogged = false;
        return hardwareInterface;
    }
    
    public ArrayList<HardwareInterface> getNameHWs() {
        return hws;
    }

    public void setNameHW(HardwareInterface hw) {
        hws.add(hw);
    }
    
//    public void setBiasgenCameraViewers (AEChip chip){
//        
//        String biasName=chip.getBiasgen().getName();
//        if (biasName=="DavisConfig") {
//            DavisConfig davisconfig= (DavisConfig) chip.getBiasgen();
////            davisconfig.setCaptureFramesEnabled(false);
//            davisconfig.setDisplayFrames(false);
//            davisconfig.setImuEnabled(false);
//            davisconfig.setDisplayImu(false);
//            chip.setBiasgen(davisconfig);
//        } else if (biasName=="Davis240Config"){
//            Davis240Config davisconfig= (Davis240Config) chip.getBiasgen();
//            davisconfig.setCaptureFramesEnabled(false);
//            davisconfig.setDisplayFrames(false);
//            davisconfig.setImuEnabled(false);
//            davisconfig.setDisplayImu(false);
//            chip.setBiasgen(davisconfig);
//        }
//    }
    
}

