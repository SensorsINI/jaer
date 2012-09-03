/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import ch.unizh.ini.jaer.projects.integrateandfire.ClusterEvent;
import ch.unizh.ini.jaer.projects.integrateandfire.Remapper;
import java.io.File;
import java.net.URL;
import jspikestack.NetController;
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
    
    NetController nc;
    SpikeStack net;
    NetMapper R;
    static File readingDir=new File(ClassLoader.getSystemClassLoader().getResource(".").getPath().replaceAll("%20", " ")+"../../subprojects/JSpikeStack/files/nets");
    
        
    public SpikeStackWrapper(NetController netCon, NetMapper rem)
    {
        nc=netCon;
        net=netCon.net;
//        net=network;
        R=rem;
    }
    
    
    public void reset()
    {   R.baseTimeSet=false; // Reinitialize baseline-time
        net.reset();
    }
    
    public void eatEvents()
    {   net.eatEvents();
    }
    
    public void eatEvents(int toTimestamp)
    {   net.eatEvents(toTimestamp);
    }
    
    public void addToQueue(Spike sp)
    {   net.addToQueue(sp);
    }
    
    public void addToQueue(BasicEvent ev)
    {   
        Spike sp=event2spike(ev);
        
        if (sp!=null)
            net.addToQueue(sp);
    }
    
//    public void addToQueue(ClusterEvent ev)
//    {   net.addToQueue(event2spike(ev));
//    }
    
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
        int addr=R.ev2addr(ev);
        
        if (addr==-1)
            return null;
        else 
        {   int layer=R.ev2layer(ev);
            if (layer!=-1)
                return new Spike(R.translateTime(ev.timestamp),addr,R.ev2layer(ev));
            else
                return null;
        }
            
    }
    
    /** Convert a cluster event to spike, using its cluster-relative position */
//    public Spike event2spike(ClusterEvent ev)
//    {
//        int addr=R.loc2addr(ev.x, ev.y,ev.source,ev.timestamp);
//        
//        if (addr<0)
//            return null;
//        else 
//            return new Spike(R.translateTime(ev.timestamp),addr,R.source2layer(ev.source));
//    }

    /**
     * @return The "enabled" status of plotting
     */
    public boolean isEnablePlotting() {
        return nc.view.enable;    }

    /** Enable plotting for the given network.  If set to true, this will launch 
     * a new plot.
     */
    public void setEnablePlotting(boolean enablePlotting) {
        nc.view.enable=enablePlotting;
        if (enablePlotting)
            nc.view.followState();
    }
    
//    public void closePlot()
//    {
//        net.plot.closePlot();
//    }
//    
}
