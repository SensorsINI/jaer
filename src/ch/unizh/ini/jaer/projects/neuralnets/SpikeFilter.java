/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import java.awt.Component;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

import jspikestack.Axon;
import jspikestack.NetController;
import jspikestack.Network;
import jspikestack.Unit;
import jspikestack.UnitLIF;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.MultiSourceProcessor;
import net.sf.jaer.graphics.DisplayWriter;


/**
 * This should be a superclass for all SpikeStack-based filters.  If you'd like 
 * to build a spiking-network filter, simply extend this class, implement all
 * abstract methods and add a constructor with (AEChip chip, int nInputs) as 
 * arguments
 * 
 * @author Peter O'Connor
 */
public abstract class SpikeFilter<AxonType extends Axon,AxonGlobalType extends Axon.Globals,UnitGlobalType extends Unit.Globals,EventType extends BasicEvent> extends MultiSourceProcessor {

    // <editor-fold  defaultstate="collapsed" desc=" Properties ">
    
    SpikeStackWrapper wrapNet;    
    Network<AxonType> net;
    AxonGlobalType axonGlobs;  // Layer Global Controls
    UnitGlobalType unitGlobs; // Unit Global Controls
        
    NetController<AxonType,Axon.Globals,UnitLIF.Globals> nc;
    
    NetController.AxonTypes axonType=NetController.AxonTypes.STP;
    NetController.UnitTypes unitType=NetController.UnitTypes.LIF;
    
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
        
        EventPacket<EventType> inputs=(EventPacket<EventType>) in;
        
//        int on=0, off=0;
        
        // Initialize Remapper
        if (wrapNet==null)
            return inputs;
        else if (!wrapNet.isRunning())
            wrapNet.start(inputs.getFirstTimestamp());
        
        if (lastEvTime==Integer.MAX_VALUE && !inputs.isEmpty())
        {   lastEvTime=inputs.getFirstTimestamp();
        }
        BasicEvent lastEvt;
        // Iterate through events
        for (int k=0; k<inputs.getSize(); k++)
        {   
            if (pause)
                return inputs;
            
            
//            if (((PolarityEvent)inputs.getEvent(k)).polarity==PolarityEvent.Polarity.On)
//                on++;
//            else
//                off++;
            
            BasicEvent ev=inputs.getEvent(k);
            
            if (lastEvTime!=Integer.MAX_VALUE && (ev.timestamp-lastEvTime<0))
            {   
                System.out.println("Non-Monotinic Timestamps detected ("+lastEvTime+"-->"+ev.timestamp+").  Discarding Event");        
                continue;
//                lastEvTime=Integer.MAX_VALUE;        
//                wrapNet.reset();                
//                return inputs;
            }
            wrapNet.addToQueue(ev);
            
            lastEvt = ev;
            lastEvTime=ev.timestamp;
        }
                
//        System.out.println("On: "+on+"\tOff: "+off);
        
//        if (inputs.getSize()>0)
//            wrapNet.flushToTime(lastEventTime);
        
        return inputs;
    }
    
    /** Create a spikeFilter with the given chip and number of input event sources */
    public SpikeFilter(AEChip chip)
    {   super(chip);    
    }
    
    @Override
    public void initFilter()            
    {
        
    }
    
    public void hardResetFilter(){
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
    }
        
    @Override
    public void resetFilter()
    {                   
        doReset_Network();
    }
        
    // </editor-fold>
    
    // <editor-fold  defaultstate="collapsed" desc=" Neural Network Builders ">
    
    /** Grab a network from file. */
    public Network makeInitialNet() {
                
        nc=new NetController(axonType,unitType);
        net=nc.net;
        net.liveMode=true;
        
        
        axonGlobs=(AxonGlobalType)nc.axonGlobals;
        unitGlobs=(UnitGlobalType)nc.unitGlobals;
        
        return net;
    }
    
    public void setNet(NetController n)
    {
        nc=n;
        net=nc.net;
        axonGlobs=(AxonGlobalType)nc.axonGlobals;
        unitGlobs=(UnitGlobalType)nc.unitGlobals;
        
        
        wrapNet=new SpikeStackWrapper(nc,makeMapper(net));
        
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
    
    
    public void setNetwork(Network net,NetMapper map)
    {
        wrapNet=new SpikeStackWrapper(nc,map);
    }
        
    int lastEvTime=Integer.MAX_VALUE;

    // </editor-fold>
    
    // <editor-fold  defaultstate="collapsed" desc=" Abstract Methods ">
        
    /** Given the network, make a NetMapper object */
    public abstract NetMapper makeMapper(Network net);
    
    /** Apply various parameters on the network. */
    public abstract void customizeNet(Network<AxonType> net);
            
    
    @Override
    public String[] getInputNames() {
        return new String[]{"Input"};
    }
    
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
        hardResetFilter();
        
        Network net=makeInitialNet();
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
        if(nc!=null)
            if (nc.view!=null)
                nc.view.reset();
        lastEvTime = Integer.MAX_VALUE;
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
