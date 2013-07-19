/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import jspikestack.AxonSTP;
import jspikestack.Network;
import jspikestack.UnitLIF;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/**
 * 
 * @author Peter
 */
@Description("Implements a spiking Restricted Boltzmann Machine for handwritten digit recognition.")
public class NumberNet extends SpikeFilter<AxonSTP,AxonSTP.Globals,UnitLIF.Globals,PolarityEvent> {

//    SpikeStack<STPLayer,Spike> net;
//    
//    STPLayer.Globals lg;
//    LIFUnit.Globals ug;
//    
    public NumberNet(AEChip chip)
    {   super(chip);
    }
    
    @Override
    public NetMapper makeMapper(Network net) {
        
//      VisualMapper<PolarityEvent> map=new VisualMapper<PolarityEvent>(){
        VisualMapper map=new VisualMapper(){
        
            @Override
//            public int ev2addr(PolarityEvent ev) {
            public int ev2addr(BasicEvent ev) {
                if (ev instanceof PolarityEvent && ((PolarityEvent)ev).polarity==PolarityEvent.Polarity.On)
                    return -1;
                else
                    return super.ev2addr(ev);

            }
        
        };
        map.inDimX=(short)chip.getSizeX();
        map.inDimY=(short)chip.getSizeY(); 
        map.outDimX=(short)net.lay(0).dimx;
        map.outDimY=(short)net.lay(0).dimy;
        return map;
        
        
        
        
        
    }

//    @Override
//    public SpikeStack getInitialNet() {
//        
//        STPLayer.Factory<STPLayer> layerFactory=new STPLayer.Factory();
//        LIFUnit.Factory unitFactory=new LIFUnit.Factory(); 
//                
//        lg= layerFactory.glob;
//        ug = unitFactory.glob;
//        
//        
//        
//        net=new SpikeStack(layerFactory,unitFactory);
//        buildFromXML(net);
////        net.read.readFromXML(net);
//        return net;
//    }

    @Override
    public void customizeNet(Network netw) {
        
        this.buildFromXML("RBM 784-500-500-10 MNIST trained.xml");
//        if (!net.isBuilt())
//            return;
        
        unitGlobs.setTau(100000);
        axonGlobs.delay=12000;
        unitGlobs.setTref(5000);
        
        
        
        
        // Set up connections
//        float[] sigf={1, 1, 0, 1};
//        nc.setForwardStrengths(new boolean[]{true,true,false,true});
//        float[] sigb={0, 1, 0, 1};
//        nc.setBackwardStrengths(new boolean[]{true,false,true,true});
        
        // Up the threshold
//        net.scaleThresholds(500);
        
        
//        for (int i=0; i<net.nLayers(); i++)
//            for (Unit u:net.lay(i).units)
//                u.thresh*=600;
        
        unitGlobs.useGlobalThresh=true;
        unitGlobs.thresh=3;
        
        
//        lg.fastWeightTC=2;
//        
//        net.lay(1).enableFastSTDP=true;
//        net.lay(3).enableFastSTDP=true;
//        
//            
//        
        ((AxonSTP)net.ax(1,2)).setEnableFastSTDP(false);
        axonGlobs.fastSTDP.plusStrength=-.001f;
        axonGlobs.fastSTDP.minusStrength=-.001f;   
        axonGlobs.fastSTDP.stdpTCminus=10;
        axonGlobs.fastSTDP.stdpTCplus=10;
//        
//        net.plot.timeScale=1f;
//        
//        net.liveMode=true;
//        net.plot.realTime=true;
//        
//        net.plot.updateMicros=100000;
        
        net.lay(0).fireInputsTo=true;
        net.lay(0).inputCurrentStrength=1.5f;
        
        net.addAllReverseAxons();
        
        net.unrollRBMs();
        
    }

    @Override
    public String[] getInputNames() {
        return new String[] {"Retina"};
    }
    
    
    public float getInputCurrentStrength() {
        
        if (net==null)
            return 0;
        
        return net.lay(0).inputCurrentStrength;
    }

    
    public void setInputCurrentStrength(float inputCurrentStrength) {
        if (net==null)
            return;
        
        this.net.lay(0).inputCurrentStrength = inputCurrentStrength;
    }
    
    

}
