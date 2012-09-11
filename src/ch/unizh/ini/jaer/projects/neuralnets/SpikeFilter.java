/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import java.awt.Component;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import jspikestack.*;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.MultiSourceProcessor;
import net.sf.jaer.graphics.DisplayWriter;
import sun.nio.cs.ext.GB18030;

/**
 * This should be a superclass for all SpikeStack-based filters.  If you'd like 
 * to build a spiking-network filter, simply extend this class, implement all
 * abstract methods and add a constructor with (AEChip chip, int nInputs) as 
 * arguments
 * 
 * @author Peter
 */
public abstract class SpikeFilter extends MultiSourceProcessor {

    // <editor-fold  defaultstate="collapsed" desc=" Properties ">
    
    SpikeStackWrapper wrapNet;    
    SpikeStack<AxonBundle> net;
    AxonBundle.Globals axonGlobs;  // Layer Global Controls
    LIFUnit.Globals unitGlobs; // Unit Global Controls
        
    NetController<AxonBundle,STPAxon.Globals,LIFUnit.Globals> nc;
    
    NetController.Types netType=NetController.Types.STP_LIF;
    
    boolean pause=false;
    
    
    // </editor-fold>
            
    // <editor-fold  defaultstate="collapsed" desc=" Obligatory Filter Methods ">
        
    /** Do standard event processing ops for the given network.  If you'd like
     *  to do more processing, it's recommended that you override this method, with 
     *  "super.filterPacket(in);" as the first line, then your custom code,
     *  followed by "return in;"
     */
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        // Initialize Remapper
        if (wrapNet==null)
            return in;
        else if (!wrapNet.R.isBaseTimeSet())
            wrapNet.R.setBaseTime(in.getFirstTimestamp());
        
//        int skipped=0;
        
//        System.out.println("FilterThread: "+in.getFirstTimestamp()/1000);
        
        if (lastEvTime==Integer.MAX_VALUE && !in.isEmpty())
        {   lastEvTime=in.getFirstTimestamp();
//            lastEv=new BasicEvent();
//            lastEv.copyFrom(in.getFirstEvent());
        }
        
        
        // If it's a clusterset event
        for (int k=0; k<in.getSize(); k++)
//        for (BasicEvent ev:in)
        {   
            
            
            if (pause)
                return in;
        
        
            
            
            
            BasicEvent ev=in.getEvent(k);
            
            if (lastEvTime!=Integer.MAX_VALUE && (lastEvTime>ev.timestamp))
            {   
                System.out.println("Non-Monotinic Timestamps detected ("+lastEvTime+"-->"+ev.timestamp+").  Resetting");                
                lastEvTime=Integer.MAX_VALUE;        
                wrapNet.reset();                
                return in;
            }
            
            wrapNet.addToQueue(ev);
            
            lastEvTime=ev.timestamp;
        }
        
//        if (skipped>0)
            
        if (lastEvTime!=Integer.MAX_VALUE)
            wrapNet.eatEvents(wrapNet.R.translateTime(lastEvTime));
                
        return in;
    }
    
    /** Create a spikeFilter with the given chip and number of input event sources */
    public SpikeFilter(AEChip chip)
    {   super(chip);    
    }
    
    @Override
    public void initFilter()
    {
    }
        
    /** This should reset the filter back to where it was before you did anything with it.*/
    @Override
    public void resetFilter()
    {           
        wrapNet=null;
        net=null;
        unitGlobs=null;
        axonGlobs=null;
        if (nc!=null)
        {   nc.view.enable=false;
        }
        super.removeControls();
        super.removeDisplays();
        
        
        nc=null;
        
        
        
//        net.plot.enable=false;
        
    }
        
    // </editor-fold>
    
    // <editor-fold  defaultstate="collapsed" desc=" Neural Network Builders ">
    
    /** Grab a network from file. */
    public SpikeStack getInitialNet() {
                
        nc=new NetController(netType);
        net=nc.net;
        net.liveMode=true;
        
        axonGlobs=nc.layerGlobals;
        unitGlobs=nc.unitGlobals;
        
        return net;
    }
    
    
    /** Build the given network from XML (you can also do this directly though
     the SpikeStack.read object, but this method will start the file finder in 
     the appropriate directory.)
     */
    public void buildFromXML()
    {
        SpikeStackWrapper.buildFromXML(net);
    }
    
    public void buildFromXML(String filename)
    {
        SpikeStackWrapper.buildFromXML(net,filename);
    }
    
    
    public void setNetwork(SpikeStack net,NetMapper map)
    {
        wrapNet=new SpikeStackWrapper(nc,map);
    }
        
    int lastEvTime=Integer.MAX_VALUE;
    
    
    
    
    // </editor-fold>
    
    // <editor-fold  defaultstate="collapsed" desc=" Abstract Methods ">
        
    /** Given the network, make a NetMapper object */
    public abstract NetMapper makeMapper(SpikeStack net);
    
    /** Apply various parameters on the network. */
    public abstract void customizeNet(SpikeStack net);
            
    // </editor-fold>
        
    // <editor-fold  defaultstate="collapsed" desc=" GUI Methods ">
    
    public class NetworkPlot implements DisplayWriter
    {

        JPanel disp; // Display panel for network
        
        @Override
        public void setPanel(JPanel imagePanel) {
//            wrapNet.net.plot.
        }

        @Override
        public Component getPanel() {
            
            if (disp==null)
            {   disp=new JPanel();
                nc.view.realTime=true;
                nc.view.followState(disp);
            }            
            
            return disp;
            
            
        }

        @Override
        public void setDisplayEnabled(boolean state) {
            wrapNet.setEnablePlotting(false);
        }
        
    }
    
    /** Grab the network from and XML file */
    public void doInitialize_Network()
    {
        initializeNetwork();
        addControls();
        plotNet(true);
        
    }
    
    /** Start the thing */
    public void initializeNetwork()
    {
        resetFilter();
        
        SpikeStack net=getInitialNet();
        customizeNet(net);
        wrapNet=new SpikeStackWrapper(nc,makeMapper(net));
        
    }
    
        
    /** Grab the network from and XML file */
//    public void doReload_Parameters()
//    {
//        customizeNet(wrapNet.net);
//    }
    
    
    public void plotNet(boolean internal)
    {
        if (wrapNet==null)
        {
            JOptionPane.showMessageDialog ( 
                null, "You need to load a network before plotting it!" );
            return;            
        }        
        
        if (internal)
        {   JPanel disp=new JPanel();
            nc.view.realTime=true;
            nc.view.followState(disp);
            super.addDisplay(disp);
        }    //super.addDisplayWriter(new NetworkPlot());
        else
            nc.startDisplay();
    }
    
    
    public void doReset_Network()
    {
        super.resynchronize();
        if (wrapNet!=null)
            wrapNet.reset();
        if (nc.view!=null)
            nc.view.reset();
        
    }
    
    public void addControls()
    {
        JPanel jp=nc.makeControlPanel();
        
        super.addControls(jp);
        
    }
        
    
    // </editor-fold>
    
    // <editor-fold  defaultstate="collapsed" desc=" Controller Methods ">
    
    public boolean isDreamMode()
    {
        if (net==null)
            return false;
        else
            return !net.liveMode;
        
    }
    
    public void setDreamMode(boolean dreamMode)
    {
        net.liveMode=!dreamMode;
        
    }
    
    
        
    // </editor-fold>
}
