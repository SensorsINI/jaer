/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JOptionPane;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.InputEventIterator;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.RetinaRenderer;

/**
 * So this guy uses binary neurons.
 * An events come in, causing its corresponding input neuron to turn on, and 
 * propagate its effect through the network.
 * The event is then put into a buffer, where it will be turned off after some 
 * number N more input events come in.
 * 
 * @author Peter
 */
public class BinNetFilt extends EventFilter2D implements FrameAnnotater {

    NetworkArray Net;            // Array of binary nets
    int inputPersistence=getInt("inputPersistence",300000);   // Input persistence, in us
    LinkedList<ClusterEvent> offBuffer=new LinkedList(); // Buffer of events to be turned off
    boolean enableNetwork=true;
    int netCount=getInt("netCount",4);
    ClusterSet C;
    Plotter plot;
    float inputThresh=getFloat("inputThresh",0);
    
    
    float[] windex;         // Records 
    float windexUpdates;
    
    
    Remapper R;         // Remapper object
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) 
    {   // Distribute incoming events to appropriate network.  
        
        if(!filterEnabled) return in;
        
        
        // int dim=128;
        //int dim=32;

        //EventPacket Pout=dickChainy.filterPacket(P);
        
        
        if (enclosedFilterChain!=null)
            in=enclosedFilterChain.filterPacket(in);
        
        if (Net==null || !enableNetwork)
        {   out=in;
            return out;
        }              
        
        int nEvents=out.getSize();
        if (nEvents==0) return out;
        
                
//        Iterator outItr=out.getEvent
        //out.setEventClass(ClusterEvent.class);
        ClusterEvent evIn=(ClusterEvent)out.getEvent(0);
        ClusterEvent evOut=(ClusterEvent)offBuffer.pollFirst();
        
        int loc;
        
        boolean state;
        ClusterEvent ev;
        int k=1;
        
        int balance=0;
        while (k<nEvents)
        {   // Iterate over the input packet
            
            // If 'on' event is next, else ...
            boolean evtState=(evOut==null || (evIn.timestamp < evOut.timestamp+inputPersistence));
            if (evtState)   // ON event
            {   ev=evIn;
                evIn=(ClusterEvent)out.getEvent(k++);
                
                // Copy the event, add to 'turnoff' buffer.
                ClusterEvent evBuf=new ClusterEvent();
                evBuf.copyFrom(ev);
                offBuffer.addLast(evBuf);
                balance++;
                //System.out.print(1);
            }
            else // OFF event
            {   ev=evOut;
                evOut=offBuffer.pollFirst();
                //System.out.print(0);
                balance--;
            }
            
            // Get the cluster index, resend the event. 
            int index=ev.getCluster().index;
            if (index==-1)
            {   //System.out.println("Unaddressed cluster.  This shouldn't happen but hey look, it did big woop.");
                continue;
            }
            
            // Get the input target index and fire
            BinNet BN=((BinNet)Net.N[index]);
            int inputIndex=BN.R.xy2ind(ev.xp, ev.yp);      
            float hit=evtState?1:-1;
            BN.hitThat(inputIndex, hit);
            //BN.setFire(inputIndex, evtState);
        }
        //System.out.println(out.getLastTimestamp());
        
        if (plot!=null) 
        {   plot.replot(out.getLastTimestamp());
        }
        setClusterTags();
        
        return out;
                
    }

    @Override
    public void resetFilter() {
        if (Net!=null)
            Net.reset();
        
        offBuffer.clear();
    }
    
    // ===== Controls =====
    
    public void doChoosePlot(){
        
        if (plot!=null)
        {   // Thread safety (am I doing it right?)
            Plotter ptemp=plot;
            plot=null;
            ptemp.dispose();
        }
        
        plot=Plotter.choosePlotter(Net);
        
//        
//        
//        Object[] options = {"LivePlotter","Number Display","Unit Probe"};
//        int n = JOptionPane.showOptionDialog(null,
//            "How you wanna display this?",
//            "Hey YOU",
//            JOptionPane.YES_NO_CANCEL_OPTION,
//            JOptionPane.QUESTION_MESSAGE,
//            null,
//            options,
//            options[1]);
//
//        switch (n){
//
//            case 0: // LivePlot Display
//                plot=new LivePlotter();
//                break;
//            case 1: // Swing Display for numbers
//                plot=new NumberReader();
//                break;
//            case 2:
//                plot=new Probe();
//                break;
//        }
//        plot.load(Net);
//        plot.init();
    }
    
    public void doShowInputMap(){
    
        //RetinaRenderer R=new RetinaRenderer(this.chip);
        
        //float[] arr = R.getPixmapArray();
    
        //R.re
    }
    
    public boolean getEnableNetwork() {
        return this.enableNetwork;
    }
    
    public void setEnableNetwork(boolean dt) {
        getPrefs().putBoolean("ClassItUp.EnableNetwork",dt);
        support.firePropertyChange("enableNetwork",this.enableNetwork,dt);
        this.enableNetwork = dt;
    }
    
    public void setInputThresh(float val) {
        for (Network n:Net.N)
            ((BinNet)n).setInputThresh(val);
        
        inputThresh=val;
        resetFilter();
    }
    
    public float getInputThresh()
    {   return inputThresh;
    }
    
    public void doLoad_Network() throws Exception
    {   
        // Initialize the remapper
        R=new Remapper();
        R.inDimX=(short)chip.getSizeX();
        R.inDimY=(short)chip.getSizeY(); 
        
        setEnableNetwork(false);
        Net=new NetworkArray(BinNet.class,netCount);
        Net.setRemapper(R);       
        Net.loadFromFile();
        
        // Set Output indeces.
        int[] outputIX=new int[10];
        for (int i=0; i<outputIX.length; i++) 
            outputIX[i]=Net.N[0].nUnits()-10+i;
        Net.setOutputIX(outputIX);
        
        Net.setTrackHistory(10);
        
        resetFilter();
        setEnableNetwork(true);
        
    }
        
    public void doPrint_States()
    {   if (Net.N==null)
        {   System.out.println("Can't print network states: No network is loaded yet.  Load one!");

        }
        for (Network n:Net.N)
        {   System.out.println("==========================================");
            ((BinNet)n).printStates();
        }
        System.out.println("==========================================");
    }
            
    public void setClusterTags()
    {   if (C==null) return; 
    
        for (ClusterSet.Cluster c:C.getClusters())
            ((ClusterSet.ExtCluster)c).tag=Net.N[((ClusterSet.ExtCluster)c).index].getWinningTag();
        
    }
    
    
    public void setNetCount(int count)
    {   
        this.filterEnabled=false;
        C.setMaxNumClusters(netCount);
        C.resetFilter();
                
        netCount=count;
        try {
            doLoad_Network(); // This may be a little lazy, but we don't currently save the file before loading.
        } catch (Exception ex) {
            Logger.getLogger(BinNetFilt.class.getName()).log(Level.SEVERE, null, ex);
        }
        //tagNetworks();
        this.filterEnabled=true;
    }
    
    public int getNetCount()
    {   return this.netCount;        
    }

    public void setInputPersistence(int v)
    {   this.inputPersistence=v;
    }
    
    public int getInputPersistence()
    {   return this.inputPersistence;        
    }
    
    
    //------------------------------------------------------
    // Obligatory method overrides
        
    //  Initialize the filter
    public  BinNetFilt(AEChip  chip){
        super(chip);
        // Oddly, chip size properties don't seem to be "ready" yet when this function is called.
        
        FilterChain dickChainy=new FilterChain(chip);
                
        // Neuron Map Filter for sparsifying input (saving cycles!)
        //NeuronMapFilter N=new NeuronMapFilter(chip);
        //N.setFilterEnabled(false);
        //N.initFilter();
        
        
        
        // Clustering Filter
        C=new ClusterSet(chip);
        C.setMaxNumClusters(netCount);
                
        dickChainy.add(new PreProcess(chip));
        //dickChainy.add(N);
        dickChainy.add(C);
        
        setEnclosedFilterChain(dickChainy);
        
        setPropertyTooltip("Network","netCount", "Number of networks to use in parallel: Will also change the number of clusters");
        setPropertyTooltip("Network","inputPersistence", "The time for which an input signal will last.");
        setPropertyTooltip("Network","enableNetwork", "Enable the network.");
        //putString("Status","Great!");
    }        
    
    // Nothing
    @Override public void initFilter(){
        setAnnotationEnabled(true);
        
        enclosedFilterChain.reset();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        
    }
    
    
    
    
}
