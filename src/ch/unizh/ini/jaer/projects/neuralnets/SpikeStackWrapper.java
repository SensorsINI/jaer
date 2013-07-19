/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import java.io.File;

import jspikestack.NetController;
import jspikestack.Network;
import jspikestack.PSP;
import jspikestack.PSPInput;
import jspikestack.PSPNull;
import net.sf.jaer.event.BasicEvent;

/**
 * Wrapper Class for the SpikeStack package.  
 * 
 * This is a wrapper instead of an extension because we want to allow for the 
 * possibility of using to wrap extensions of SpikeStack.
 * 
 * @author oconnorp
 */
public class SpikeStackWrapper {
    
    NetController nc;
    Network net;
    NetMapper mapper;
    static File readingDir=new File(ClassLoader.getSystemClassLoader().getResource(".").getPath().replaceAll("%20", " ")+"../../subprojects/JSpikeStack/files/nets");
    Thread netThread;
        
    public SpikeStackWrapper(NetController netCon, NetMapper rem)
    {
        nc=netCon;
        net=netCon.net;
//        net=network;
        mapper=rem;
    }
    
    public boolean isRunning()
    {
        return (netThread!=null);
        
    }
    
    
    public void reset()
    {   mapper.baseTimeSet=false; // Reinitialize baseline-time
        net.reset();
    }
    
    
    
    public void eatEvents()
    {   net.eatEvents();
    }
    
    public void start(int baselineEventTime)
    {
        mapper.setBaseTime(baselineEventTime);
        
        if (netThread!=null)
            throw new RuntimeException("Can't start network... it's already running");
            
        netThread=net.startEventFeast();
    }
    
    
    
    public void kill()
    {
        netThread.interrupt();
        
        netThread=null;
    }
    
    
    public void eatEvents(int toTimestamp)
    {   net.eatEvents(toTimestamp);
    }
    
    public void flushToTime(int eventTimeStamp)
    {
        PSP p=new PSPNull(mapper.translateTime(eventTimeStamp));
        addToQueue(p);
    }
    
    public void addToQueue(PSP sp)
    {   net.addToQueue(sp);
    }
    
    public void addToQueue(BasicEvent ev)
    {   
        PSPInput sp=event2spike(ev);
        
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
    public static void buildFromXML(Network net)
    {
//        URL f=ClassLoader.getSystemClassLoader().getResource(".");
        
        net.read.readFromXML(net,readingDir);
    }    
    
    /** Build the input network from XML */
    public static void buildFromXML(Network net, String relativeFileName)
    {
//        URL f=ClassLoader.getSystemClassLoader().getResource(".");
        
        File f=new File(readingDir+"/"+relativeFileName);
        
        if (f.isFile())
            net.read.readFromXML(net,f);
        else
            net.read.readFromXML(net,readingDir);
    }  
            
    /** Convert a basic event to spike */
    public PSPInput event2spike(BasicEvent ev)
    {
        return mapper.mapEvent(ev);
//        int addr=R.ev2addr(ev);
//        
//        if (addr==-1)
//            return null;
//        else 
//        {   int layer=R.ev2layer(ev);
//            if (layer!=-1)
//                return new PSPInput(R.translateTime(ev.timestamp),addr,R.ev2layer(ev));
//            else
//                return null;
//        }
    }
    
    /** Convert a basic event to spike */
//    public PSPInput event2spike(PolarityEvent ev)
//    {
//        return R.mapEvent(ev);
////        int addr=R.ev2addr(ev);
////        
////        if (addr==-1)
////            return null;
////        else 
////        {   int layer=R.ev2layer(ev);
////            if (layer!=-1)
////                return new PSPInput(R.translateTime(ev.timestamp),addr,R.ev2layer(ev),(ev.polarity==PolarityEvent.Polarity.On)?1:-1);
////            else
////                return null;
////        }
//    }
    
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
