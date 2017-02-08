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

import eu.seebetter.ini.chips.davis.Davis240Config;
import java.util.ArrayList;
import java.util.TreeMap;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.stereopsis.MultiCameraBiasgenHardwareInterface;
import net.sf.jaer.stereopsis.MultiCameraHardwareInterface;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.stereopsis.MultiCameraInterface;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.OutputEventIterator;

import net.sf.jaer.event.MultiCameraApsDvsEvent;
import net.sf.jaer.event.ApsDvsEvent;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import eu.seebetter.ini.chips.davis.DavisConfig;
import eu.seebetter.ini.chips.davis.DavisTowerBaseConfig;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.graphics.AEViewer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import net.sf.jaer.graphics.ImageDisplay;

import net.sf.jaer.graphics.TwoCamera3DDisplayMethod;
import net.sf.jaer.graphics.MultiViewMultiCamera;

import java.util.Arrays;
import java.util.logging.LogManager;


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
    private JFrame apsFrame = null;
    public ImageDisplay[] apsDisplay= new ImageDisplay[NUM_CAMERAS];
    private ImageDisplay.Legend apsDisplayLegend;
    private int displaycamera=NUM_CAMERAS; 
    private float[] displayFrame;
    private boolean displayAPSEnable=false;
    
    public ArrayList<HardwareInterface> hws = new ArrayList();
    
    private int sx;
    private int sy;
    private int ADCMax;

    private float[] resetBuffer, signalBuffer;
    private float[] displayBuffer;
    private float[] apsDisplayPixmapBuffer;

    int count0=0;
    int count1=0;


    private JMenu multiCameraMenu = null;

//    public MulticameraDavisRenderer  MultiDavisRenderer;

    /** Creates a new instance of  */
    public MultiDavisCameraChip() {
        
        super();
        LogManager.getLogManager().reset();
        setName("MultiDavisCameraChip");
        setEventClass(MultiCameraApsDvsEvent.class);
        setEventExtractor(new Extractor(this));
        
        getCanvas().addDisplayMethod(new TwoCamera3DDisplayMethod(getCanvas()));
        getCanvas().addDisplayMethod(new MultiViewMultiCamera(getCanvas()));
  
    }
    
    @Override
    public void onDeregistration() {
        super.onDeregistration();
        if (getAeViewer() == null) {
            return;
        }
        if (multiCameraMenu != null) {
            getAeViewer().removeMenu(multiCameraMenu);
            multiCameraMenu = null;
        }
        if (camerasAEVs != null) {
            for (int i=0; i<camerasAEVs.length; i++){
                if(camerasAEVs[i]!=null){
                    camerasAEVs[i].setVisible(false);
                }
            }
        }
    }

    @Override
    public void onRegistration() {
        super.onRegistration();
        if (getAeViewer() == null) {
            return;
        }
        multiCameraMenu = new JMenu("MultiCameraMenu");
        multiCameraMenu.add(new JMenuItem(new SelectCamera()));
        multiCameraMenu.add(new JSeparator());
        multiCameraMenu.add(new JMenuItem(new ApsDisplay()));
        multiCameraMenu.add(new JMenuItem(new createMultipleAEViewer()));
        getAeViewer().addMenu(multiCameraMenu);
    }
    
    
    final public class createMultipleAEViewer extends DavisMenuAction {

        public createMultipleAEViewer() {
            super("CreateMultipleAEViewer", "Show each camera view on different viewers", "CreateMultipleAEViewer");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_E, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            
            mainAEV=getMultiChip().getAeViewer();    
            JAERV=mainAEV.getJaerViewer();
            NUM_VIEWERS= JAERV.getViewers().size();
                     
            while (NUM_VIEWERS<NUM_CAMERAS+1) {
                
                int i=NUM_VIEWERS-1;
                
                camerasAEVs[i]= new AEViewer(JAERV,chip.getClass().getName());//,chip.getClass().getName()
                
                setBiasgenCameraViewers (camerasAEVs[i].getChip());
                camerasAEVs[i].setVisible(true);
                
                if (hws.size()!=0){
                    camerasAEVs[i].getChip().setHardwareInterface(hws.get(i));
                } else {
                    log.warning("No Harwdware Interfaces found!");
                }

                NUM_VIEWERS= JAERV.getViewers().size();
            }
            
        }
    }
    
    public void setBiasgenCameraViewers (AEChip chip){
        
        String biasName=chip.getBiasgen().getName();
        if (biasName=="DavisConfig") {
            DavisConfig davisconfig= (DavisConfig) chip.getBiasgen();
            davisconfig.setDisplayFrames(false);
            davisconfig.setDisplayImu(false);
            chip.setBiasgen(davisconfig);
        } 
        if (biasName=="Davis240Config"){
            Davis240Config davisconfig= (Davis240Config) chip.getBiasgen();
            davisconfig.setDisplayFrames(false);
            davisconfig.setDisplayImu(false);
            chip.setBiasgen(davisconfig);
        }
        if (biasName=="DavisTowerBaseConfig"){
            DavisTowerBaseConfig davisconfig= (DavisTowerBaseConfig) chip.getBiasgen();
            davisconfig.setDisplayFrames(false);
            davisconfig.setDisplayImu(false);
            chip.setBiasgen(davisconfig); 
        }
    }
    
    
    final public class ApsDisplay extends DavisMenuAction {

        public ApsDisplay() {
            super("ApsDisplay", "Display APS", "Display the sorted event in frames");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_E, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            displayAPSEnable=true;
            for (int c=0; c< NUM_CAMERAS; c++){
                apsDisplay[c] = ImageDisplay.createOpenGLCanvas();
                apsFrame = new JFrame("APS Camera " + c);
                apsFrame.setPreferredSize(new Dimension(400, 400));
                apsFrame.getContentPane().add(apsDisplay[c], BorderLayout.CENTER);
                apsFrame.setVisible(true);
                apsFrame.pack();
                apsDisplayLegend = apsDisplay[c].addLegend("", 0, 0);
                final float[] displayColor = new float[3];
                displayColor[0] = 1.0f;
                displayColor[1] = 1.0f;
                displayColor[2] = 1.0f;
                apsDisplayLegend.color = displayColor;
                apsDisplay[c].setImageSize(sx, sy);
                resetBuffer = new float[sx * sy];
                signalBuffer = new float[sx * sy];
                displayFrame = new float[sx * sy];
                displayBuffer = new float[sx * sy];
                apsDisplayPixmapBuffer = new float[3 * sx * sy];
                Arrays.fill(resetBuffer, 0.0f);
                Arrays.fill(signalBuffer, 0.0f);
                Arrays.fill(displayFrame, 0.0f);
                Arrays.fill(displayBuffer, 0.0f);
                Arrays.fill(apsDisplayPixmapBuffer, 0.0f);
            }
        }
    }
    
    final public class SelectCamera extends DavisMenuAction {

        public SelectCamera() {
            super("SelectCamera", "Select which camera display", "SelectCamera");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_E, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
        Object[] possibilities = new Object[NUM_CAMERAS+1];
        for (int i=0; i<=NUM_CAMERAS; i++){
            possibilities[i]=i;
        }
        Frame frame=new Frame();
        displaycamera = (int)JOptionPane.showInputDialog(frame,"Select camera to display:","Choose Camera",JOptionPane.QUESTION_MESSAGE,null,possibilities,displaycamera);
        
        }
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
    
    public void setADCMax(int ADCmaxValue){
        ADCMax=ADCmaxValue;
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
        synchronized public ApsDvsEventPacket extractPacket(AEPacketRaw in) {
            final int sx = getChipType().getSizeX()-1;
            final int sy = getChipType().getSizeY()-1;
            if (out == null) {
                out = new ApsDvsEventPacket(MultiCameraApsDvsEvent.class);
            }else {
                out.clear();
            }
            if (in == null) {
                return (ApsDvsEventPacket) out;
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

                }else if (e.isApsData()){
                    e.camera = MultiCameraApsDvsEvent.getCameraFromRawAddressAPS(address);
//                    System.out.println("DVS? "+ e.isDVSEvent()+" camera: " +e.camera+" x: "+ e.x+" y: "+e.y);
                }
                
                if(displaycamera<NUM_CAMERAS){
                    int chosencamera=displaycamera;
                    if(e.camera==chosencamera){
                        e.setFilteredOut(true);
                    }
                }             
                
                if (e.isApsData() & displayAPSEnable){
                    putAPSEventInBuffer(e);
                }

            }
            return (ApsDvsEventPacket) out;

        }
        
        public void putAPSEventInBuffer(MultiCameraApsDvsEvent e) {
                int c=e.camera;
                final ApsDvsEvent.ReadoutType type = e.getReadoutType();
                final float val = e.getAdcSample();
                apsDisplay[c].setPixmapGray(e.x, e.y, val / ADCMax);
                apsDisplay[c].repaint();               
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
        
        log.info(n + " interfaces found!");
        if (n <1) {
            log.warning( " couldn't build MultiCameraHardwareInterface hardware interface because only " + n + " camera is available and at least 2 cameras are needed");
            hardwareInterface= HardwareInterfaceFactory.instance().getInterface(n);
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
            hardwareInterface.close(); // will be opened later on by user

        } catch (Exception e) {
            log.warning("couldn't build correct multi camera hardware interface: " + e.getMessage());
            return null;
        }
        deviceMissingWarningLogged = false;
        return hardwareInterface;
    }
    
    public ArrayList<HardwareInterface> getHWs() {
        return hws;
    }

    public void setHWs(HardwareInterface hw) {
        hws.add(hw);
    }
    
    
}

