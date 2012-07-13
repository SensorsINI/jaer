/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import java.awt.Component;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import jspikestack.SpikeStack;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.MultiSourceProcessor;
import net.sf.jaer.graphics.DisplayWriter;

/**
 * This should be a superclass for all SpikeStack-based filters.  If you'd like 
 * to build a spiking-network filter, simply extend this class, implement all
 * abstract methods and add a constructer with (AEChip chip, int nInputs) as 
 * arguments
 * 
 * @author Peter
 */
public abstract class SpikeFilter extends MultiSourceProcessor {

    SpikeStackWrapper wrapNet;
    
    /** Create a spikeFilter with the given chip and number of input event sources */
    public SpikeFilter(AEChip chip, int sensoryInputs)
    {   super(chip,sensoryInputs);    
    }
    
    /** Return an initialized network */
    public abstract SpikeStack getInitialNet();
    
    /** Given the network, make a NetMapper object */
    public abstract NetMapper makeMapper(SpikeStack net);
    
    /** Apply various parameters on the network. */
    public abstract void customizeNet(SpikeStack net);
        
    // ---------------- Implemented Filter Methods -----------------------------
    
    @Override
    public void initFilter()
    {
    }
    
    /** Build the given network from XML (you can also do this directly though
     the SpikeStack.read object, but this method will start the file finder in 
     the appropriate directory.)
     */
    public void buildFromXML(SpikeStack net)
    {
        SpikeStackWrapper.buildFromXML(net);
    }
    
    @Override
    public void resetFilter()
    {   if (wrapNet!=null)
            wrapNet.reset();        
    }
    
    public void setNetwork(SpikeStack net,NetMapper map)
    {
        wrapNet=new SpikeStackWrapper(net,map);
    }
        
    BasicEvent lastEv=null;
    
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
        
        
        
        // If it's a clusterset event
        for (BasicEvent ev:in)
        {   wrapNet.addToQueue(ev);
            
            if (lastEv!=null && (lastEv.timestamp>ev.timestamp))
                  System.out.println("Non-mon!");
            
            lastEv=ev;
        }
        
        wrapNet.eatEvents();
                
        return in;
    }
    
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
                wrapNet.net.plot.followState(disp);
            }            
            
            return disp;
            
            
        }

        @Override
        public void setDisplayEnabled(boolean state) {
            wrapNet.setEnablePlotting(false);
        }
        
    }
    
    
    // ------------------ Gui Filter Methods -----------------------------------
    
    /** Grab the network from and XML file */
    public void doInitialize_Network()
    {
        SpikeStack net=getInitialNet();
        wrapNet=new SpikeStackWrapper(net,makeMapper(net));
        customizeNet(net);
    }
    
    /** Start plotting the network */
    public void doPlot_Network()
    {
        if (wrapNet==null)
        {
            JOptionPane.showMessageDialog ( 
                null, "First grab a network!" );
            return;            
        }
        
        super.addDisplayWriter(new NetworkPlot());
        
        
    }
    
}
