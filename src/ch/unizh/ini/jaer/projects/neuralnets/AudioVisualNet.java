/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import java.util.logging.Level;
import java.util.logging.Logger;

import jspikestack.AxonSTP;
import jspikestack.Network;
import jspikestack.UnitLIF;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/**
 * 
 * 
 * @author Peter
 */
@Description("Implements an associative memory between audio and visual stimuli using a network of spiking neurons.")
public class AudioVisualNet extends SpikeFilter<AxonSTP,AxonSTP.Globals,UnitLIF.Globals,BasicEvent> {
    
    public AudioVisualNet(AEChip chip)
    {   super(chip);  // 2 inputs
        
    }

    @Override
    public NetMapper makeMapper(Network net) {
        
        
        final ISIMapper isi=new ISIMapper(64,100);
        isi.setMinFreqHz(350);
        isi.setMaxFreqHz(1200);
//        isi.setMaxFreqHz(2000);
        
        
        final VisualMapper map=new VisualMapper();
        map.inDimX=(short)chip.getSizeX();
        map.inDimY=(short)chip.getSizeY(); 
        map.outDimX=(short)net.lay(0).dimx;
        map.outDimY=(short)net.lay(0).dimy;
        
        if (map.outDimX==0)
            throw new RuntimeException("Chip Appears to have 0 dimension.  This will mess up the mapping of events.");
        
        return new NetMapper(){
//        return new NetMapper<BasicEvent>(){

            @Override
            public int ev2addr(BasicEvent ev) {
                if (ev.source==0)
                {   return map.ev2addr(ev);
                }
                else if (ev.source==1)
                {   return isi.ev2addr(ev);
                }
                else 
                    throw new UnsupportedOperationException("Source bit "+ev.source+"does not map to any layer!");
                
            }
            
            @Override
            public int ev2layer(BasicEvent ev)
            {
                if (ev.source==0)
                    if (((PolarityEvent) ev).polarity==PolarityEvent.Polarity.On)
                        return -1;
                    else 
                        return 0;
                else
                    return 2;
            }
            
            
        };
    }

    @Override
    public void customizeNet(Network neet) {
        
        
        this.buildFromXML("AssociativeNetworkE.xml");
        
        this.setMaxWaitTime(300000);
        
        // Add inhibitory laterals to the audio input layer.
//        AxonBundle.Factory fac=new AxonBundle.Factory();
//        AxonBundle ax=fac.make(net.lay(2), net.lay(2));
//        ax.setAllWeights(-2);
        
        if (!net.isBuilt())
            return;
        
//        NetController<STPLayer,STPLayer.Globals,LIFUnit.Globals> netcon= nc;
        
        
        
//        net.unrollRBMs();
        
        AxonSTP.Globals lG=(AxonSTP.Globals)axonGlobs;
        
        unitGlobs.tau=100000;
        lG.delay=12000;
        unitGlobs.tref=5000;
        
//        nc.view.timeScale=1f;
//        
        
        // Set up connections
//        net.ax(0,1)
//        nc.setForwardStrengths(new boolean[] {true,true,false,true});
//        nc.setBackwardStrengths(new boolean[] {true,true,false,true});
        
        
        // Up the threshold
        unitGlobs.useGlobalThresh=true;
        unitGlobs.thresh=2;
        
        
        
        lG.fastWeightTC=2000000;
        
//        ((STPAxon)net.ax(1,2)).setEnableFastSTDP(true);
//        ((STPAxon)net.ax(3,2)).setEnableFastSTDP(true);        
//        lG.fastSTDP.plusStrength=(-.01f);
//        lG.fastSTDP.minusStrength=(-.01f);   
//        lG.fastSTDP.stdpTCminus=(10000);
//        lG.fastSTDP.stdpTCplus=(10000);
//        
        
        ((AxonSTP)net.ax(1,4)).setEnableFastSTDP(false);
        ((AxonSTP)net.ax(2,4)).setEnableFastSTDP(false);
        ((AxonSTP)net.ax(3,4)).setEnableFastSTDP(false);
//        un.thresh=1.5f;
        
        
        axonGlobs.stdpWin=30000;
        axonGlobs.fastSTDP.plusStrength=-.0002f;
        axonGlobs.fastSTDP.minusStrength=-.0002f;   
        axonGlobs.fastSTDP.stdpTCminus=20000;
        axonGlobs.fastSTDP.stdpTCplus=20000;
        
        net.lay(0).fireInputsTo=true;
        net.lay(0).inputCurrentStrength=.5f;
        net.lay(2).fireInputsTo=true;
        net.lay(2).inputCurrentStrength=.1f;
        
        setVisualInputStrength(getVisualInputStrength());
        setAudioInputStrength(getAudioInputStrength());
        
        net.addAllReverseAxons();
        net.unrollRBMs();
        
        
        net.lay(0).name="VI (up)";
        net.lay(1).name="VA";
        net.lay(2).name="AU";
        net.lay(3).name="LB";
        net.lay(4).name="AS";
        net.lay(5).name="VI (down)";
        
        net.liveMode=true;
        
    }

    @Override
    public String[] getInputNames() {
        return new String[] {"Retina","Cochlea"};
    }
    
    
    float visualInputStrength=.5f;
    public void setVisualInputStrength(float s)
    {   net.lay(0).inputCurrentStrength=visualInputStrength=s;
    }
    public float getVisualInputStrength()
    {   return visualInputStrength;        
    }
    
    float audioInputStrength=.1f;
    public void setAudioInputStrength(float s)
    {   net.lay(2).inputCurrentStrength=audioInputStrength=s;
    }
    public float getAudioInputStrength()
    {   return audioInputStrength;        
    }

    // Set filter to startup disabled, regardless
    @Override 
    public void setPreferredEnabledState() {
        setFilterEnabled(false);
    }
    
    @Override
    public void setDreamMode(boolean dreamMode)
    {
        
        
        pause=true;
        
        // Nasty Little Hack!
        try {
            Thread.sleep(50);
        } catch (InterruptedException ex) {
            Logger.getLogger(AudioVisualNet.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
        
        net.liveMode=!dreamMode;
        
        net.ax(0,1).enable=!dreamMode;
        
        unitGlobs.tau*=dreamMode?2:.5;
        unitGlobs.thresh*=dreamMode?1/1.8f:1.8;
        
        
        ((AxonSTP)net.ax(1,4)).setEnableFastSTDP(dreamMode);
        ((AxonSTP)net.ax(2,4)).setEnableFastSTDP(dreamMode);
        ((AxonSTP)net.ax(3,4)).setEnableFastSTDP(dreamMode);
        
               
//        super.setDreamMode(dreamMode);
              
        pause=false;
        
    }
    
    
}
