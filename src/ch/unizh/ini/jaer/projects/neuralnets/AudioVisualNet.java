/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import jspikestack.*;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author Peter
 */
public class AudioVisualNet extends SpikeFilter {

    
    public AudioVisualNet(AEChip chip)
    {   super(chip,2);  // 2 inputs
        
    }

    @Override
    public NetMapper makeMapper(SpikeStack net) {
        
        final SingleSourceVisualMapper map=new SingleSourceVisualMapper();
        map.inDimX=(short)chip.getSizeX();
        map.inDimY=(short)chip.getSizeY(); 
        map.outDimX=net.lay(0).dimx;
        map.outDimY=net.lay(0).dimy;
        
        if (map.outDimX==0)
            throw new RuntimeException("Chip Appears to have 0 dimension.  This will mess up the mapping of events.");
        
        return new NetMapper(){

            @Override
            public int loc2addr(short xloc, short yloc, byte source) {
                if (source==0)
                {   return map.loc2addr(xloc,yloc);
                }
                else if (source==1)
                {   return xloc;
                }
                else 
                    throw new UnsupportedOperationException("Source bit "+source+"does not map to any layer!");
                
            }
            
            @Override
            public int source2layer(byte source)
            {
                // Map retina events to layer 0, cochlea events to layer 3.
                if (source==0)
                    return 0;
                else if (source==1)
                    return 3;
                else 
                    throw new UnsupportedOperationException("Source bit "+source+"does not map to any layer!");
            }
            
        };
    }

    @Override
    public void customizeNet(SpikeStack neet) {
        
        if (!net.isBuilt())
            return;
        
//        NetController<STPLayer,STPLayer.Globals,LIFUnit.Globals> netcon= nc;
        
        
        unitGlobs.tau=200000;
        net.delay=10000;
        unitGlobs.tref=5000;
        
        net.plot.timeScale=1f;
        
        
        // Set up connections
        nc.setForwardStrengths(new boolean[] {true,true,false,true});
        nc.setBackwardStrengths(new boolean[] {true,true,false,true});
        
        
        // Up the threshold
        unitGlobs.useGlobalThresh=true;
        unitGlobs.thresh=2;
        
        
        layGlobs.fastWeightTC=2;
        
        net.lay(1).setEnableFastSTDP(true);
        net.lay(3).setEnableFastSTDP(true);        
        layGlobs.fastSTDP.plusStrength=(-.01f);
        layGlobs.fastSTDP.minusStrength=(-.01f);   
        layGlobs.fastSTDP.stdpTCminus=(10000);
        layGlobs.fastSTDP.stdpTCplus=(10000);
        
        
        net.plot.timeScale=1f;
        
        net.liveMode=true;
        net.plot.realTime=true;
        
        net.plot.updateMicros=100000;
        
        net.inputCurrents=true;
        
        
        setVisualInputStrength(getVisualInputStrength());
        setAudioInputStrength(getAudioInputStrength());
        
        net.unrollRBMs();
        
    }

    @Override
    public String[] getInputNames() {
        return new String[] {"Retina","Cochlea"};
    }
    
    
    float visualInputStrength=2;
    public void setVisualInputStrength(float s)
    {   net.lay(0).inputCurrentStrength=visualInputStrength=s;
    }
    public float getVisualInputStrength()
    {   return visualInputStrength;        
    }
    
    float audioInputStrength=2;
    public void setAudioInputStrength(float s)
    {   net.lay(3).inputCurrentStrength=audioInputStrength=s;
    }
    public float getAudioInputStrength()
    {   return audioInputStrength;        
    }
    
}
