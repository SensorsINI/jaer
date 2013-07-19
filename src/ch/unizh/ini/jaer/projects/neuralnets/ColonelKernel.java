/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import jspikestack.AxonSparse;
import jspikestack.KernelMaker2D;
import jspikestack.NetController;
import jspikestack.Network;
import jspikestack.UnitLIF;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.PolarityEvent;

/**
 *
 * @author Peter
 */
@Description("Applies a convolutional Kernel on the inputs.")
public class ColonelKernel extends SpikeFilter<AxonSparse,AxonSparse.Globals,UnitLIF.Globals,PolarityEvent>{

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
//        net.addLayer(3).initializeUnits(64, 64);
        net.addAxon(0,1);
        net.addAxon(0,2);
//        net.addAxon(1,3);
//        net.addAxon(2,3);
                
        // Step 2: Define Feedforward Kernel
        KernelMaker2D.MexiHat k01=new KernelMaker2D.MexiHat();
        k01.setAngle(90);
        k01.mag=1;
        k01.ratio=1.8f;
        k01.majorWidth=8;
        k01.ratioCenterSurround=1;
        k01.squeezeFactorCenter=4;
        k01.squeezeFactorSurround=2;
        net.ax(0,1).setKernelControl(k01, 7, 7);
        
        KernelMaker2D.MexiHat k02=new KernelMaker2D.MexiHat();
        k02.setAngle(0);
        k02.mag=1;
        k02.ratio=1.8f;
        k02.majorWidth=8;
        k02.ratioCenterSurround=1;
        k02.squeezeFactorCenter=4;
        k02.squeezeFactorSurround=2;
        net.ax(0,2).setKernelControl(k02, 7, 7);
        
//        KernelMaker2D.Gaussian k13=new KernelMaker2D.Gaussian();
//        k13.mag=1;
//        k13.majorWidth=3;
//        k13.minorWidth=3;
//        net.ax(1,3).setKernelControl(k13, 5,5);
//        
//        KernelMaker2D.Gaussian k23=new KernelMaker2D.Gaussian();
//        k23.mag=1;
//        k23.majorWidth=3;
//        k23.minorWidth=3;
//        net.ax(1,3).setKernelControl(k23, 5,5);
        
        
        // Step 4: Define global neuron parameters
        
//        unitGlobs=(UnitOnOff.Globals)unitGlobs;
        
        unitGlobs.useGlobalThresh=true;
        unitGlobs.thresh=3;
        unitGlobs.tau=50000;
        unitGlobs.tref=5000;        
        
        // Step 5: Confirm that network is ready to run.
        net.check();
        
        nc.getControls().buildersEnabled=true;
//        nc.view.zeroCentred=true;
        
    }

    
}
