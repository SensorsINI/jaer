/*
 * AEMonitorTest.java
 *
 * Created on October 21, 2005, 9:22 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.aemonitor;

import ch.unizh.ini.caviar.biasgen.*;
import ch.unizh.ini.caviar.biasgen.BiasgenHardwareInterface;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.eventprocessing.filter.BackgroundActivityFilter;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.eventprocessing.label.SimpleOrientationFilter;
import ch.unizh.ini.caviar.chip.retina.Tmpdiff128;
import ch.unizh.ini.caviar.eventprocessing.tracking.ClusterTracker;
import ch.unizh.ini.caviar.graphics.*;
//import ch.unizh.ini.caviar.graphics.OrientationRenderer;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceFactory;
import java.awt.event.*;

/**
 * Tests AE monitor capability.
 * @author tobi
 */
public class AEMonitorTest {
    
    static AEChip chip;
    static EventExtractor2D extractor=null;
    static BiasgenFrame biasgenFrame=null;
    static Biasgen biasgen=null;
    static EventFilter2D filter1=null, filter2=null;
    static RetinaRenderer renderer=null;
    static ClusterTracker tracker=null;
    static AEMonitorInterface aemon=null;
    
    /** Creates a new instance of AEMonitorTest */
    private AEMonitorTest() {
    }
    
    static class Stopper implements ActionListener{
        public boolean stop=false;
        
        
        public void actionPerformed(ActionEvent actionEvent) {
            stop=true;
        }
    }
    
    static long delay=100;
    static void setDelay(long d){
        delay=d;
//        System.out.println("delay="+delay);
    }
    
    static void open(){
        if(aemon!=null && aemon.isOpen()) return;
        try{
            aemon=(AEMonitorInterface)HardwareInterfaceFactory.instance().getFirstAvailableInterface();
            
            if(aemon==null) return;
            aemon.open();
            // note it is important that this open succeeed BEFORE aemon is assigned to biasgen, which immeiately tries to open and download biases, creating a storm of complaints if not sucessful!
            
            if(aemon instanceof BiasgenHardwareInterface){
                if(chip==null) {
                    chip=new Tmpdiff128(aemon); // makes biasgen
                }else{
                    chip.setHardwareInterface(aemon); // if we do this, events do not start coming again after reconnect of device
                }
                extractor=chip.getEventExtractor();
                if(biasgenFrame==null) {
                    biasgenFrame=new BiasgenFrame(chip);  // should check if exists...
                }
                biasgenFrame.setVisible(true);
                filter1=new BackgroundActivityFilter(chip);
                filter2=new SimpleOrientationFilter(chip);
                renderer=new RetinaRenderer(chip);
                filter1.setFilterEnabled(true);
                filter2.setFilterEnabled(true);
                tracker=new ClusterTracker(chip);
                
            }
        }catch(Exception e){
            e.printStackTrace();
            aemon.close();
        }
    }
    
//    /**
//     * Tests event acquisition.
//     *
//     * @param args the command line arguments
//     */
//    public static void main(String[] args) {
//        
//        int i=0;
//        int numDevices=HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
//        System.out.println(numDevices+" devices found");
//        if(numDevices==0) System.exit(0);
//        final JFrame frame=new JFrame("AEMonitorTest");
//        Box box=new Box(BoxLayout.X_AXIS);
//        frame.setContentPane(box);
//        JButton sb=new JButton("Stop");
//        box.add(sb);
//        final JSlider sl=new JSlider(1,200);
//        setDelay(1000);
//        box.add(sl);
//        sl.addChangeListener(new ChangeListener(){
//            public void stateChanged(javax.swing.event.ChangeEvent e){
//                setDelay(sl.getValue()*10);
//            }
//        });
//        
//        Stopper stopper=new Stopper();
//        sb.addActionListener(stopper);
//        
//        frame.pack();
//        frame.setVisible(true);
//        
//        
//        AEPacketRaw ae=null;
//        while(stopper.stop==false){
//            try {Thread.currentThread().sleep(delay);} catch (java.lang.InterruptedException e) {}
//            i++;
//            int numEvents=0;
//            open();
//            if(aemon==null || !aemon.isOpen()) continue;
//            if(aemon.overrunOccurred()){
//                System.out.print("overrun ");
//            }
//            try{
//                ae=aemon.acquireAvailableEventsFromDriver();
//            }catch(HardwareInterfaceException e){
//                e.printStackTrace();
//                aemon=null;
//                continue;
//            }
//            numEvents=ae.getNumEvents();
//            AEPacket2D ae2=extractor.extract(ae);
//            ae2=filter1.filter(ae2);
//            //ae2=filter2.filter(ae2);
//            float [][][] fr=renderer.render(ae2);
//            tracker.filter(ae2);
//            
//            numEvents=ae2.getNumEvents();
//            if(numEvents==0) System.out.println("no events");
//            if(numEvents>0){
//                System.out.print(numEvents+" events: ");
//                short[] addr=ae.getAddresses();
//                int [] ts=ae.getTimestamps();
//                if(addr==null) continue; // didn't get anything
//                int k=numEvents<10?numEvents:10;
//                for(int j=0;j<k;j++){
//                    short a=addr[j];
//                    short x=(short)((a&0x7e)>>1); // map to retina address space
//                    short y=(short)((a&0x3f00)>>8);
//                    byte pol=(byte)((a&1)==1?1:-1);
//                    System.out.print(x+","+y+","+ts[j]+" ");
//                }
//                System.out.println("");
//            }
//        }
//        if(aemon!=null) aemon.close();
//        System.out.println("closed");
//        System.exit(0);
//    }
    
    
}
