/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import java.awt.GridBagLayout;
import java.io.File;
import jspikestack.STPStack;
import jspikestack.SpikeStack;
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
    
    File startDir=new File(getClass().getClassLoader().getResource(".").getPath().replaceAll("%20", " ")+"../../subprojects/JSpikeStack/files/nets");
    
    
    public SpikeFilter(AEChip chip)
    {   super(chip);
    }
        
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        // Initialize Remapper
        if (netArr==null)
            return in;
        else if (!netArr.R.isBaseTimeInitialized())
            netArr.R.initializeBaseTime(in.getFirstTimestamp());
        
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
        
        netArr.crunch();
                
        return in;
    }
    
    /** Grab the network */
    public void doGrab_Network()
    {
        
        /* Step 1: Grab the network */
        SpikeStack net=new SpikeStack();
//        STPStack<STPStack,STPStack.Layer> net = new STPStack();
        
        net.read.readFromXML(net,startDir);    
       
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
        net.inputCurrentStrength=.1f;
        
        
//        STPStack<STPStack,STPStack.Layer> net2=net.read.copy();
        
//        net.eatEvents(10000);
        
        Remapper R=new Remapper();
        R.inDimX=(short)chip.getSizeX();
        R.inDimY=(short)chip.getSizeY(); 
        R.outDimX=net.lay(0).dimx;
        R.outDimY=net.lay(0).dimy;
        R.addSourcePair((byte)0, 0);

        netArr=new NetworkList(net,R);
    }

    public void doPlot_Network()
    {
        netArr.setPlottingState(0, true);
//        this.chip.getAeViewer().getContentPane().setLayout(new GridBagLayout());
//        this.chip.getAeViewer().getContentPane().add(netArr.initialNet.plot.getFrame().getContentPane());
    }
    
    
    @Override
    public void resetFilter() {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void initFilter() {
        
        
        
//        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    
    
    
    
}
