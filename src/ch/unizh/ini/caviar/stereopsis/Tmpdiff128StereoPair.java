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

package ch.unizh.ini.caviar.stereopsis;

import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.aemonitor.AEMonitorInterface;
import ch.unizh.ini.caviar.aemonitor.AEPacketRaw;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.chip.retina.Tmpdiff128;
import ch.unizh.ini.caviar.chip.retina.sensorymotor.Batter;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.graphics.*;
import ch.unizh.ini.caviar.graphics.RetinaCanvas;
import ch.unizh.ini.caviar.graphics.BinocularRenderer;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterface;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceFactory;

/**
 * A stereo pair of Tmpdiff128 retinas. Differs from the usual AEChip object in that it also overrides #getHardwareInterface and #setHardwareInterface
 to supply StereoHardwareInterface which is a pair of Tmpdiff128 hardware interfaces.
 * @author tobi
 */
public class Tmpdiff128StereoPair extends Tmpdiff128 implements StereoChipInterface {
    
    AEChip left=new Tmpdiff128(), right=new Tmpdiff128();
    
    /** Creates a new instance of Tmpdiff128StereoPair */
    public Tmpdiff128StereoPair() {
        super();
        
        setEventClass(BinocularEvent.class);
        setRenderer(new BinocularRenderer(this));
//        setCanvas(new RetinaCanvas(this)); // we make this canvas so that the sizes of the chip are correctly set
        setEventExtractor(new Extractor(this));
        setBiasgen(new Biasgen(this));
        setLeft(left);
        setRight(right);
        
        getFilterChain().add(new StereoTranslateRotate(this));
        getFilterChain().add(new StereoVergenceFilter(this));
        getFilterChain().add(new GlobalDisparityFilter(this));
        getFilterChain().add(new GlobalDisparityFilter2(this));
        getFilterChain().add(new DisparityFilter(this));
        getFilterChain().add(new StereoClusterTracker(this));
        getFilterChain().add(new Batter(this));
//        getRealTimeFilterChain().add(new Batter(this));
//        if(filterFrame!=null) filterFrame.dispose();
//        filterFrame=new FilterFrame(this);
    }
    
    @Override public void setAeViewer(AEViewer aeViewer) {
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
        this.left=left;
    }
    
    public void setRight(AEChip right) {
        this.right=right;
    }
    
    /**
     * swaps the left and right hardware channels. This method can be used if the hardware interfaces are incorrectly assigned.
     */
    public void swapEyes() {
        AEChip tmp=getLeft();
        setLeft(getRight());
        setRight(tmp);
    }
    
    @Override public int getNumCellTypes(){
            return 4;
    }
    
    /** the event extractor for the stereo chip pair. It extracts from each event the x,y,type of the event and in addition, it adds getNumCellTypes to each type to signal
     * a right event (as opposed to a left event)
     */
    public class Extractor extends Tmpdiff128.Extractor implements java.io.Serializable{
        public Extractor(Tmpdiff128StereoPair chip){
            super(new Tmpdiff128()); // they are the same type
        }
        
        /** extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if(out==null){
                out=new EventPacket(BinocularEvent.class);
            }
            if(in==null) return out;
            int n=in.getNumEvents(); //addresses.length;
            
            int skipBy=1;
            if(isSubSamplingEnabled()){
                while(n/skipBy>getSubsampleThresholdEventCount()){
                    skipBy++;
                }
            }
            short[] a=in.getAddresses();
            int[] timestamps=in.getTimestamps();
            OutputEventIterator outItr=out.outputIterator();
            for(int i=0;i<n;i+=skipBy){ // bug here
                BinocularEvent e=(BinocularEvent)outItr.nextOutput();
                e.timestamp=timestamps[i];
                e.x=getXFromAddress(a[i]);
                e.y=getYFromAddress(a[i]);
                e.type=getTypeFromAddress(a[i]);
                e.polarity=e.type==0?PolarityEvent.Polarity.Off:PolarityEvent.Polarity.On;
                e.eye=Stereopsis.isRightRawAddress(a[i])? BinocularEvent.Eye.RIGHT: BinocularEvent.Eye.LEFT;
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
        
        /** Reconstructs the raw packet after event filtering to include the binocular information 
         @param packet the filtered packet
         @return the reconstructed packet
         */
        @Override public AEPacketRaw reconstructRawPacket(EventPacket packet) {
            AEPacketRaw p=super.reconstructRawPacket(packet);
            // we also need to add binocularity (eye) to raw events
            for(int i=0;i<packet.getSize();i++){
                BinocularEvent be=(BinocularEvent)packet.getEvent(i);
                if(be.eye==BinocularEvent.Eye.RIGHT){
                    EventRaw event=p.getEvent(i);
                    event.address&=Stereopsis.MASK_RIGHT_ADDR;
                }
            }
            return p;
        }
        
    }
    @Override public void setHardwareInterface(HardwareInterface hw){
        if(hw!=null){
            log.warning("trying to set hardware interface to "+hw+" but hardware interface is built as StereoHardwareInterface by this device");
        }
        if(hw!=null && hw.isOpen()) {
            log.info("closing hw interface");
            hw.close();
        }
        super.setHardwareInterface(hw);
    }
    
    boolean deviceMissingWarningLogged=false;
    
    /**Builds and returns a StereoHardwareInterface for this stereo pair of devices. Unlike other chip objects, this one actually invokes the HardwareInterfaceFactory to
     * construct the interfaces, because this device depends on a particular pair of interfaces.
     * @return the hardware interface for this device
     */
    @Override public HardwareInterface getHardwareInterface() {
        if(hardwareInterface!=null) return hardwareInterface;
        int n=HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
        if(n<2){
            if(deviceMissingWarningLogged=false){
                log.warning("couldn't build AEStereoRetinaPair hardware interface because only "+n+" are available");
                deviceMissingWarningLogged=true;
            }
            return null;
        }
        HardwareInterface hw0=HardwareInterfaceFactory.instance().getInterface(0);
        HardwareInterface hw1=HardwareInterfaceFactory.instance().getInterface(1);
        try{
            hardwareInterface=new StereoBiasgenHardwareInterface((AEMonitorInterface)hw0,(AEMonitorInterface)hw1);
        }catch(ClassCastException e){
            log.warning("couldn't build correct stereo hardware interface: "+e.getMessage());
            return null;
        }
        deviceMissingWarningLogged=false;
        return hardwareInterface;
    }
    
    /**
     * A paired biasgen for this stereo combination of Tmpdiff128. The biases are simultaneously controlled.
     * @author tobi
     */
    public class Biasgen extends Tmpdiff128.Biasgen {
        
        /** Creates a new instance of Biasgen for Tmpdiff128 with a given hardware interface
         *@param chip the hardware interface on this chip is used
         */
        public Biasgen(ch.unizh.ini.caviar.chip.Chip chip) {
            super(chip);
            setName("Tmpdiff128StereoPair");
        }
    }
}
