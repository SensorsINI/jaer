/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import ch.unizh.ini.jaer.projects.integrateandfire.ClusterEvent;
import ch.unizh.ini.jaer.projects.integrateandfire.Remapper;
import java.io.File;
import java.net.URL;
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
    NetMapper R;
    static File readingDir=new File(ClassLoader.getSystemClassLoader().getResource(".").getPath().replaceAll("%20", " ")+"../../subprojects/JSpikeStack/files/nets");
    
        
    public SpikeStackWrapper(NetType network, NetMapper rem)
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
    
    /** Build the existing network from XML */
    public void buildFromXML()
    {
        buildFromXML(net);
    }    
    
    /** Build the input network from XML */
    public static void buildFromXML(SpikeStack net)
    {
//        URL f=ClassLoader.getSystemClassLoader().getResource(".");
        
        net.read.readFromXML(net,readingDir);
    }    
            
    /** Convert a basic event to spike */
    public Spike event2spike(BasicEvent ev)
    {
        return new Spike(R.loc2addr(ev.x, ev.y,ev.source),R.translateTimeDouble(ev.timestamp,.001f),R.source2layer(ev.source));
    }
    
//    /** Convert a cluster event to spike, using its cluster-relative position */
//    public Spike event2spike(ClusterEvent ev)
//    {
//        return new Spike(R.loc2addr(ev.xp, ev.yp,ev.source),R.translateTimeDouble(ev.timestamp,.001f),R.source2layer(ev.source));
//    }

    /**
     * @return The "enabled" status of plotting
     */
    public boolean isEnablePlotting() {
        return net.plot.enable;    }

    /** Enable plotting for the given network.  If set to true, this will launch 
     * a new plot.
     */
    public void setEnablePlotting(boolean enablePlotting) {
        net.plot.enable=enablePlotting;
        if (enablePlotting)
            net.plot.followState();
    }
    
//    public void closePlot()
//    {
//        net.plot.closePlot();
//    }
//    
}
