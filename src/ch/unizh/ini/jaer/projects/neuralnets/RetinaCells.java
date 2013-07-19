/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import jspikestack.AxonSparse;
import jspikestack.NetController;
import jspikestack.Network;
import jspikestack.UnitLIF;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/**
 *
 * In this filter we implement a variety of retina cells.  
 *  
 * 
 * @Description("This filter implements PV5 approach-sensitive cells.  These 
 * ganglion cells respond specifically to dark approaching objects.  See more at
 * http://www.nature.com/neuro/journal/v12/n10/extref/nn.2389-S1.pdf")
 * 
 * @author Peter O'Connor
 */
public class RetinaCells extends SpikeFilter<AxonSparse,AxonSparse.Globals,UnitLIF.Globals,PolarityEvent> {

    public RetinaCells(AEChip chip)
    {   super(chip);
    
        this.axonType=NetController.AxonTypes.SPARSE;
    }
    
    @Override
    public NetMapper makeMapper(Network net) {
//        return new NetMapper<PolarityEvent>()
        return new NetMapper()
        {
            @Override
            public int ev2addr(BasicEvent ev) {
                return 127-ev.y+128*ev.x;
            }
                        
            /** Map the source byte onto the layer index */
            @Override
            public int ev2layer(BasicEvent ev) {
            	if (ev instanceof PolarityEvent)
            	   return ((PolarityEvent)ev).polarity==PolarityEvent.Polarity.On?1:0;
            	else 
            		return super.ev2layer(ev);
            }            
           
        };
    }

    @Override
    public void customizeNet(Network<AxonSparse> net) {
        
//        Network.Initializer ini=new Network.Initializer();
//        ini.lay(0).dimx=ini.lay(0).dimy=128;    // ON layer
//        ini.lay(1).dimx=ini.lay(1).dimy=128;    // OFF layer
//        ini.lay(2).dimx=ini.lay(2).dimy=16;     // PV-5 layer
//        ini.ax(0,2);
//        ini.ax(1,2);
//        net.buildFromInitializer(ini);
        
        // See this method if you're interested in how this network is built.
        nc=jspikestack.JspikeStack.grabRetinaNetwork();
        
        
        this.setNet(nc);
        
//        net.addLayer(0).initializeUnits(128, 128);
//        net.addLayer(1).initializeUnits(128, 128);
//        net.addLayer(2).initializeUnits(10,10);
//        net.addLayer(3).initializeUnits(64, 64);
//        net.addAxon(0,2);
//        net.addAxon(1,2);
//        
//        
//        net.lay(0).name="OFF cells";
//        net.lay(1).name="ON cells";
//        net.lay(2).name="PV5 cells";
//        net.lay(3).name="OFF brisk";
//        
////        updateOnKernel();
////        updateOffKernel();
//        
//        
//        KernelMaker2D.Gaussian k02=new KernelMaker2D.Gaussian();
//        k02.mag=.15f;
//        k02.majorWidth=40;
//        net.ax(0,2).setKernelControl(k02, 50, 50);
//        
//        KernelMaker2D.Gaussian k12=new KernelMaker2D.Gaussian();
//        k12.mag=-.3f;
//        k12.majorWidth=70;
//        net.ax(1,2).setKernelControl(k12, 90, 90);
//        
//        
//        unitGlobs.tau=36000;
//        unitGlobs.tref=7000;
//        unitGlobs.useGlobalThresh=true;
//        unitGlobs.thresh=1.5f;
//        
//        axonGlobs.delay=0;
//        
//        net.lay(0).fireInputsTo=false;
//        net.lay(1).fireInputsTo=false;
//        net.inputCurrents=false;
        
        
    }
    

    @Override
    public String[] getInputNames() {
        return new String[] {"Retina"};
    }
    
    
//    public float offKernelMag=.15f/

    
}
