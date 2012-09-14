/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import jspikestack.AxonSparse;
import jspikestack.KernelMaker2D;
import jspikestack.NetController;
import jspikestack.Network;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author Peter
 */
@Description("Applies a convolutional Kernel on the inputs.")
public class ColonelKernel extends SpikeFilter<AxonSparse>{

    public ColonelKernel(AEChip chip)
    {   super(chip);
    
        axonType=NetController.AxonTypes.SPARSE;
        unitType=NetController.UnitTypes.ONOFFLIF;
    
    }
        
    @Override
    public NetMapper makeMapper(Network net) {
        VisualMapper vi=new VisualMapper();
        
        vi.inDimX=(short)chip.getSizeX();
        vi.inDimY=(short)chip.getSizeY();
        vi.outDimX=(short)net.lay(0).dimx;
        vi.outDimY=(short)net.lay(0).dimy;
        
        return vi;
    }

    @Override
    public void customizeNet(Network<AxonSparse> net) {
        
        // Step 1: Define Network Architecture
        net.addLayer(0).initializeUnits(128, 128);
        net.addLayer(1).initializeUnits(64, 64);
        net.addLayer(2).initializeUnits(64, 64);
        net.addAxon(0,1);
        net.addAxon(0,2);
                
        // Step 2: Define Feedforward Kernel
        KernelMaker2D.MexiHat k01=new KernelMaker2D.MexiHat();
        k01.angle=90;
        k01.mag1=1;
        k01.mag2=.5f;
        k01.majorWidth1=4;
        k01.minorWidth1=1;
        k01.majorWidth2=4;
        k01.minorWidth2=2;   
        net.ax(0,1).setKernelControl(k01, 7, 7);
        
        KernelMaker2D.MexiHat k02=new KernelMaker2D.MexiHat();
        k02.angle=0;
        k02.mag1=1;
        k02.mag2=.5f;
        k02.majorWidth1=4;
        k02.minorWidth1=1;
        k02.majorWidth2=4;
        k02.minorWidth2=2;  
        net.ax(0,2).setKernelControl(k02, 7, 7);
        
        // Step 4: Define global neuron parameters
        unitGlobs.useGlobalThresh=true;
        unitGlobs.thresh=3;
        unitGlobs.tau=50000;
        unitGlobs.tref=5000;        
        
        // Step 5: Confirm that network is ready to run.
        net.check();
        
        
        nc.view.zeroCentred=true;
        
    }

    
}
