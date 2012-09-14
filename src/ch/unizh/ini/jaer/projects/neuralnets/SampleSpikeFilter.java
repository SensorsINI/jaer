/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import jspikestack.NetController;
import jspikestack.AxonSTP;
import jspikestack.Network;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.PolarityEvent;

/**
 * This is a simple example implementation of a Spiking Filter.  Input events 
 * from the Retina are fed into a 64x64 input layer.  Spike from this layer are 
 * fed through a random connection matrix to a 16x16 output layer.
 * @author Peter
 */
public class SampleSpikeFilter extends SpikeFilter{

    public SampleSpikeFilter(AEChip chip)
    {   super(chip);
    }
    
    /** Return an object that maps the retina input to the input layer of the network*/
    @Override
    public NetMapper makeMapper(Network net) {
        VisualMapper<PolarityEvent> map=new VisualMapper<PolarityEvent>();
        map.inDimX=(short)chip.getSizeX();
        map.inDimY=(short)chip.getSizeY(); 
        map.outDimX=(short)net.lay(0).dimx;
        map.outDimY=(short)net.lay(0).dimy;
        return map;
    }

    /** Build the network */
    @Override
    public void customizeNet(Network net) {
               
        Network.Initializer ini=new Network.Initializer();
        
        ini.lay(0).dimx=ini.lay(0).dimy=64;    // Input Layer size
        ini.lay(1).dimx=ini.lay(1).dimy=16;     // Output Layer size
        ini.ax(0,1).wMean=0;                // Mean of randomly initialized weights
        ini.ax(0,1).wStd=.1f;               // STD of randomly initialized weights
        
        net.buildFromInitializer(ini);
        
        net.lay(0).name="Input Layer";
        net.lay(1).name="Output Layer";
        
        unitGlobs.tau=50000;                // Membrane time constant in us
        unitGlobs.tref=7000;                // Refractory period in us
        unitGlobs.useGlobalThresh=true;     // Use same thresh for all units
        unitGlobs.thresh=1;                 // Firing threshold
        
        // Set Axon Parameters        
        AxonSTP.Globals ag=(AxonSTP.Globals)this.axonGlobs;
        ag.delay=2000;                      // Axonal delay in us
        ag.stdpWin=30000;                   // STDP window width (us)
        ag.stdp.plusStrength=.0001f;        // pre-post kernel magnitude
        ag.stdp.minusStrength=-.0001f;      // post-pre kernel magnitude
        ag.stdp.stdpTCplus=20000;           // pre-post kernel width
        ag.stdp.stdpTCminus=10000;          // post-pre kernel width
                
        net.lay(0).fireInputsTo=true;             // Fire to, rather than from, the input units
        net.lay(0).inputCurrentStrength=0.1f;       // Effect of each input event on input layer  
        ((AxonSTP)net.ax(0,1)).setEnableSTDP(true); // Enable STDP learning on this axon
        
    }

    /** Define Names of your input source(s)*/
    @Override
    public String[] getInputNames() 
    {   return new String[]{"Retina"};
    }
}
