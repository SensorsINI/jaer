/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import jspikestack.STPStack;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * This filter contains one or more networks and routes events to them.  
 * 
 * @author oconnorp
 */
public class SpikeFilter extends EventFilter2D{

    NetworkList netArr;
    
    public SpikeFilter(AEChip chip)
    {   super(chip);
    }
        
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        // Initialize Remapper
        if (netArr.R==null)
        {   
            
        }
        
        // If it's a clusterset event
        if (in.getEventClass()==ClusterSet.class)
        {
            
            for (BasicEvent ev:in)
            {   netArr.routeTo((ClusterEvent)ev);
            }
        }
        else
        {   // Otherwise, route all events to network 0.
            for (BasicEvent ev:in)
            {   netArr.routeTo(ev,0);
            }
        }
                
        return out;
    }
    
    /** Grab the network */
    public void doGrab_Network()
    {
        
        /* Step 1: Grab the network */
//        SpikeStack net=new 
        STPStack<STPStack,STPStack.Layer> net = new STPStack();
        net.read.readFromXML(net);    
       
        net.tau=100f;
        net.delay=10f;
        net.tref=5;
        
        net.plot.timeScale=1f;
        
        // Set up connections
        float[] sigf={0, 1, 0, 1};
        net.setForwardStrength(sigf);
        float[] sigb={1, 1, 0, 1};
        net.setBackwardStrength(sigb);
        
        // Up the threshold
        net.scaleThresholds(400);
        
        net.fastWeightTC=2;
        
        net.lay(1).enableFastSTDP=true;
        net.lay(3).enableFastSTDP=true;
        
        
        net.fastSTDP.plusStrength=-.001f;
        net.fastSTDP.minusStrength=-.001f;   
        net.fastSTDP.stdpTCminus=10;
        net.fastSTDP.stdpTCplus=10;
        
        net.plot.timeScale=1f;
        
        STPStack<STPStack,STPStack.Layer> net2=net.read.copy();
        
        net.eatEvents(10000);
        
        
        Remapper R=new Remapper();
        R.inDimX=(short)chip.getSizeX();
        R.inDimY=(short)chip.getSizeY(); 
        R.outDimX=netArr.getNet(0).net.lay(0).dimx;
        R.outDimY=netArr.getNet(0).net.lay(0).dimy;
        netArr.setRemapper(R);

        netArr=new NetworkList(net,R);
        
        
        
        
    }

    @Override
    public void resetFilter() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void initFilter() {
        
        
        
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    
    
    
    
}
