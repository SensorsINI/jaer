/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import jspikestack.Spike;
import jspikestack.SpikeStack;
import net.sf.jaer.event.BasicEvent;

/**
 * Wrapper Class for the SpikeStack package.  
 * 
 * This is a wrapper instead of an extension because we want to allow for the 
 * possibility of using to wrap extensions of SpikeStack.
 * 
 * @author oconnorp
 */
public class SpikeStackWrapper <NetType extends SpikeStack> {
    
    NetType net;
    Remapper R;
        
    public SpikeStackWrapper(NetType network, Remapper rem)
    {
        net=network;
        R=rem;
    }
    
    public void reset()
    {   net.reset();
    }
    
    public void eatEvents()
    {   net.eatEvents();
    }
    
    public void addToQueue(Spike sp)
    {   net.addToQueue(sp);
    }
    
    public void addToQueue(BasicEvent ev)
    {   net.addToQueue(event2spike(ev));
    }
    
    public void addToQueue(ClusterEvent ev)
    {   net.addToQueue(event2spike(ev));
    }
    
    public void readFromXML()
    {
        net.read.readFromXML(net);
    }    
            
    /** Convert a basic event to spike */
    public Spike event2spike(BasicEvent ev)
    {
        byte source=0;
        return new Spike(R.xy2ind(ev.x, ev.y),R.timeStamp2doubleTime(ev.timestamp,.001f),R.source2dest(source));
    }
    
    /** Convert a cluster event to spike, using its cluster-relative position */
    public Spike event2spike(ClusterEvent ev)
    {
        byte source=0;
        return new Spike(R.ixy2ind(ev.xp, ev.yp),R.timeStamp2doubleTime(ev.timestamp,.001f),R.source2dest(source));
    }

    /**
     * @return The "enabled" status of plotting
     */
    public boolean isEnablePlotting() {
        return net.plot.enable;    }

    /** Enable plotting for the given network.  If set to true, this will launch 
     * a new plot.
     */
    public void setEnablePlotting(boolean enablePlotting) {
        net.plot.enable=true;
        if (enablePlotting)
            net.plot.followState();
    }
    
    public void closePlot()
    {
        net.plot.closePlot();
    }
    
}
