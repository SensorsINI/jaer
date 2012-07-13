/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import jspikestack.SpikeStack;
import net.sf.jaer.chip.AEChip;

/**
 * 
 * @author Peter
 */
public class NumberNet extends SpikeFilter {

    SpikeStack net;
    
    public NumberNet(AEChip chip)
    {   super(chip,2);
    }
    
    @Override
    public NetMapper makeMapper(SpikeStack net) {
        
        SingleSourceVisualMapper map=new SingleSourceVisualMapper();
        map.inDimX=(short)chip.getSizeX();
        map.inDimY=(short)chip.getSizeY(); 
        map.outDimX=net.lay(0).dimx;
        map.outDimY=net.lay(0).dimy;
        return map;
        
    }

    @Override
    public SpikeStack getInitialNet() {
        net=new SpikeStack();
        buildFromXML(net);
//        net.read.readFromXML(net);
        return net;
    }

    @Override
    public void customizeNet(SpikeStack netw) {
               
        if (!net.isBuilt())
            return;
        
        net.tau=200f;
        net.delay=10f;
        net.tref=5;
        
        net.plot.timeScale=1f;
        
        // Set up connections
        float[] sigf={1, 1, 0, 0};
        net.setForwardStrength(sigf);
        float[] sigb={0, 0, 0, 1};
        net.setBackwardStrength(sigb);
        
        // Up the threshold
        net.scaleThresholds(500);
        
//        net.fastWeightTC=2;
//        
//        net.lay(1).enableFastSTDP=true;
//        net.lay(3).enableFastSTDP=true;
//        
//        
//        net.fastSTDP.plusStrength=-.001f;
//        net.fastSTDP.minusStrength=-.001f;   
//        net.fastSTDP.stdpTCminus=10;
//        net.fastSTDP.stdpTCplus=10;
        
        net.plot.timeScale=1f;
        
        net.liveMode=true;
        net.plot.realTime=true;
        
        net.plot.updateMillis=100;
        
        net.inputCurrents=true;
        net.inputCurrentStrength=.5f;
        
    }

    @Override
    public String[] getInputNames() {
        return new String[] {"Retina"};
    }

}
