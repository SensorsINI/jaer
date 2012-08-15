/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import jspikestack.*;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;

/**
 *
 * @author Peter
 */
public class AudioVisualNet extends SpikeFilter {

    
    public AudioVisualNet(AEChip chip)
    {   super(chip);  // 2 inputs
        
    }

    @Override
    public NetMapper makeMapper(SpikeStack net) {
        
        final VisualMapper map=new VisualMapper();
        map.inDimX=(short)chip.getSizeX();
        map.inDimY=(short)chip.getSizeY(); 
        map.outDimX=net.lay(0).dimx;
        map.outDimY=net.lay(0).dimy;
        
        if (map.outDimX==0)
            throw new RuntimeException("Chip Appears to have 0 dimension.  This will mess up the mapping of events.");
        
        return new NetMapper(){

//            @Override
//            public int loc2addr(short xloc, short yloc, byte source) {
//                if (source==0)
//                {   return map.loc2addr(xloc,yloc);
//                }
//                else if (source==1)
//                {   return xloc;
//                }
//                else 
//                    throw new UnsupportedOperationException("Source bit "+source+"does not map to any layer!");
//                
//            }
////            
//            @Override
//            public int source2layer(byte source)
//            {
//                // Map retina events to layer 0, cochlea events to layer 3.
//                if (source==0)
//                    return 0;
//                else if (source==1)
//                    return 3;
//                else 
//                    throw new UnsupportedOperationException("Source bit "+source+"does not map to any layer!");
//            }

            @Override
            public int ev2addr(BasicEvent ev) {
                if (ev.source==0)
                {   return map.ev2addr(ev);
                }
                else if (ev.source==1)
                {   return ev.x;
                }
                else 
                    throw new UnsupportedOperationException("Source bit "+ev.source+"does not map to any layer!");
                
            }
            
        };
    }

    @Override
    public void customizeNet(SpikeStack neet) {
        
        if (!net.isBuilt())
            return;
        
//        NetController<STPLayer,STPLayer.Globals,LIFUnit.Globals> netcon= nc;
        
        
        net.addAllReverseAxons();
        
        net.unrollRBMs();
        
        STPAxon.Globals lG=(STPAxon.Globals)axonGlobs;
        
        unitGlobs.tau=200000;
        lG.delay=10000;
        unitGlobs.tref=5000;
        
        nc.view.timeScale=1f;
        
        
        // Set up connections
//        net.ax(0,1)
//        nc.setForwardStrengths(new boolean[] {true,true,false,true});
//        nc.setBackwardStrengths(new boolean[] {true,true,false,true});
        
        
        // Up the threshold
        unitGlobs.useGlobalThresh=true;
        unitGlobs.thresh=2;
        
        
        lG.fastWeightTC=2;
        
        ((STPAxon)net.ax(1,2)).setEnableFastSTDP(true);
        ((STPAxon)net.ax(3,2)).setEnableFastSTDP(true);        
        lG.fastSTDP.plusStrength=(-.01f);
        lG.fastSTDP.minusStrength=(-.01f);   
        lG.fastSTDP.stdpTCminus=(10000);
        lG.fastSTDP.stdpTCplus=(10000);
        
        
        nc.view.timeScale=1f;
        
        net.liveMode=true;
        nc.view.realTime=true;
        
        nc.view.updateMicros=100000;
        
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
