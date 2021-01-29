/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import com.jogamp.opengl.GLAutoDrawable;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;


import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * So this guy uses binary neurons.
 * An events come in, causing its corresponding input neuron to turn on, and
 * propagate its effect through the network.
 * The event is then put into a buffer, where it will be turned off after some
 * number N more input events come in.
 *
 * @author Peter
 */
@Description("Tracks and classifies images of digits seen by the DVS using a network of binary-threshold neurons.")
public class BinNetFilt extends EventFilter2D implements FrameAnnotater {

    // Settings
    boolean enableNetwork=true;                     // Pass events through net?
    int netCount=getInt("netCount",4);              // Number of networks
    float inputThresh=getFloat("inputThresh",0);    // Input Threshold
    int inputPersistence=getInt("inputPersistence",200000);   // Input persistence, in us
    int Memory=getInt("inputPersistence",10);
    File networkFile=new File(getClass().getClassLoader().getResource(".").getPath().replaceAll("%20", " ")+"filterSettings/NeuralNets/MLP 784-100-50-10 shaken.xml");

    // Internal Variables
    NetworkArray Net;               // Array of binary nets
    LinkedList<ClusterEvent> offBuffer=new LinkedList(); // Buffer of events to be turned off
    ClusterSet C;                   // ClusterSet Filter
    Plotter plot;                   // Plotter Object
    ClusterEvent evOut;             // Need to store this event between filter calls
    Remapper R;                     // Remapper object

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in)  {   // Distribute incoming events to appropriate network.

        if(!filterEnabled) {
			return in;
		}

        // Filter Input Events
        if (enclosedFilterChain!=null) {
			out=enclosedFilterChain.filterPacket(in);
		}



        if (Net==null) {
			loadNetwork();
		}


        // Stop if network is not loaded
        int nEvents=out.getSize();
        if ((Net==null) || !enableNetwork || (nEvents==0))
        {   //out=in;
            return out;
        }

        // Break if no events
//        int nEvents=out.getSize();
//        if (nEvents==0) return out;


        // Grab first input/output events.
        // Check if timestamps have wrapped.  Since it happens so rarely, it's not worth
        // dealing with properly, so we'll just reset when it does.

//        if (out.getEventClass()==PolarityEvent.class)
//            return out;

        ClusterEvent evIn=(ClusterEvent)out.getEvent(0);
        if (evOut==null) {
			evOut=offBuffer.pollFirst();
		}
		else if (evIn.timestamp<evOut.timestamp) {
			resetFilter();
		}


        // Declare loop variables
        int loc;
        boolean state;
        ClusterEvent ev;
        int k=1;
        int index;

        // Iterate over the input packet
        while (k<nEvents)
        {
            // If 'on' event is next, else ...
            boolean evtState=((evOut==null) || (evIn.timestamp < (evOut.timestamp+inputPersistence)));
            if (evtState)   // ON event
            {   ev=evIn;
                evIn=(ClusterEvent)out.getEvent(k++);

                index=ev.clusterid;
                if (index==-1)
                {   //System.out.println("Unaddressed cluster.  This shouldn't happen but hey look, it did big woop.");
                    continue;
                }

                // Copy the event, appendCopy to 'turnoff' buffer.
                ClusterEvent evBuf=new ClusterEvent();
                evBuf.copyFrom(ev);
                offBuffer.addLast(evBuf);
//                balance++;
                //System.out.print(1);
            }
            else // OFF event
            {   ev=evOut;
                evOut=offBuffer.pollFirst();
                //System.out.print(0);
                index=ev.clusterid;
//                balance--;
            }

            // Relay event to appropriate network according to cluster membership
            BinNet BN=((BinNet)Net.N[index]);
            int inputIndex=BN.R.xy2ind(ev.xp, ev.yp);
            float hit=evtState?1:-1;
            BN.hitThat(inputIndex, hit);
            //BN.setFire(inputIndex, evtState);
        }

        // Plot, if a plotter is selected
        if (plot!=null)
        {   plot.replot(out.getLastTimestamp());
        }

        // Annotate winning members to appropriate cluster
        setClusterTags();

        return out;

    }

    @Override
    public void resetFilter() {
        if (Net!=null) {
			Net.reset();
		}
//        balance=0;
        offBuffer.clear();
    }

    public void loadNetwork(boolean useDefaultFile)  {
        if (!useDefaultFile) {
			try {
                File parent;
                if ((networkFile!=null) && networkFile.exists()) {
					parent=networkFile.getParentFile();
				}
				else {
					parent=null;
				}
                networkFile=Network.getfile(parent);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(BinNetFilt.class.getName()).log(Level.SEVERE, null, ex);
        }
		}
        loadNetwork();
    }


    public void loadNetwork() {
        // Initialize the remapper

        setEnableNetwork(false);
        try {
            Net=new NetworkArray(BinNet.class,netCount);
        } catch (Exception ex) {
            Logger.getLogger(BinNetFilt.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            Net.loadFromFile(networkFile);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(BinNetFilt.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(BinNetFilt.class.getName()).log(Level.SEVERE, null, ex);
        }

        // Set dimensions of screen as input dims for remapper.
        Net.R.inDimX=(short)chip.getSizeX();
        Net.R.inDimY=(short)chip.getSizeY();

        // Set Output indeces.
        int[] outputIX=new int[10];
        for (int i=0; i<outputIX.length; i++) {
			outputIX[i]=(Net.N[0].nUnits()-10)+i;
		}
        Net.setOutputIX(outputIX);

        setMemory(Memory);

        resetFilter();
        setEnableNetwork(true);

    }

    void setRequiredDefaults(){

        C.setFilterEventsEnabled(true);


    }


    // ===== Controls =====


//    public void doStart(){
//        loadNetwork();
//    }

    public void doDefault_Settings()
    {
        // ClusterSet Settings
        setRequiredDefaults();
        C.setSmoothMove(true);
        C.setSmoothWeight(2);
        C.setSmoothIntegral(0.01f);
        C.setSmoothPosition(0.01f);
        C.setSurroundInhibitionEnabled(true);
        C.setClusterMassDecayTauUs(500000);
        C.setDesiredScale(40);
        C.setShowPaths(false);

        this.setInputPersistence(200000);

        resetFilter();

    }

    public void doLoad_New_Network()
    {   loadNetwork(false);
    }

    public void doView_Units(){

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


    /** Print the state of the network, for debugging purposes */
    public void printStates()
    {   if (Net.N==null)
        {   System.out.println("Can't print network states: No network is loaded yet.  Load one!");

        }
        for (Network n:Net.N)
        {   System.out.println("==========================================");
            ((BinNet)n).printStates();
        }
        System.out.println("==========================================");
    }


    /*
    public void doStart() throws Exception   {  // URL classpath=new URL(getClass().getClassLoader().getResource(".").getPath());
        //String loc=getClass().getClassLoader().getResource(".").getPath();
        loadNetwork();
    }

    public void doShowInputMap(){
        //RetinaRenderer R=new RetinaRenderer(this.chip);

        //float[] arr = R.getPixmapArray();

        //R.re
    }
    */


    public boolean getEnableNetwork() {
        return this.enableNetwork;
    }

    public void setEnableNetwork(boolean dt) {
        getPrefs().putBoolean("ClassItUp.EnableNetwork",dt);
        support.firePropertyChange("enableNetwork",this.enableNetwork,dt);
        this.enableNetwork = dt;
    }

    public void setInputThresh(float val) {
        for (Network n:Net.N) {
			((BinNet)n).setInputThresh(val);
		}

        inputThresh=val;
        resetFilter();
    }

    public float getInputThresh()    {   return inputThresh;
    }

    public void setMemory(int val) {
        if (Net==null) {
			return;
		}
        Memory=val;
        Net.setTrackHistory(val);

    }

    public int getMemory()    {
        if (Net==null) {
			return -1;
		}
        return Memory;
    }

    public void setClusterTags()    {   if (C==null) {
		return;
	}

        for (ClusterSet.Cluster c:C.getClusters()) {
			((ClusterSet.ExtCluster)c).tag=Net.N[((ClusterSet.ExtCluster)c).index].getWinningTag();
		}

    }

    public void setNetCount(int count)    {
        this.filterEnabled=false;
        C.setMaxNumClusters(count);


        netCount=count;
        try {
            loadNetwork();
        } catch (Exception ex) {
            Logger.getLogger(BinNetFilt.class.getName()).log(Level.SEVERE, null, ex);
        }
        //tagNetworks();
        this.filterEnabled=true;
    }

    public int getNetCount()    {   return this.netCount;
    }

    public void setInputPersistence(int v)    {   this.inputPersistence=v;
    }

    public int getInputPersistence()    {   return this.inputPersistence;
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
        C.setFilterEventsEnabled(true);
        C.setShowPaths(false);
        C.setSmoothIntegral(.025f);
        C.setSmoothWeight(2);
        C.setSmoothMove(true);
        C.setClusterMassDecayTauUs(100000);

        dickChainy.add(new PreProcess(chip));
        //dickChainy.appendCopy(N);
        dickChainy.add(C);

        setEnclosedFilterChain(dickChainy);

        setPropertyTooltip("netCount", "Number of networks to use in parallel: Will also change the number of clusters");
        setPropertyTooltip("inputPersistence", "The time for which an input signal will last.");
        setPropertyTooltip("enableNetwork", "Enable the network.");
        setPropertyTooltip("inputThresh", "Threshold of the input units.  Setting this higher makes it more noise-tolerant.");
        setPropertyTooltip("memory", "(approximately) how many cycles over which to smooth the output in order to determine a winner");



    }

    // Nothing
    @Override public void initFilter(){
        setAnnotationEnabled(true);
        enclosedFilterChain.reset();
        doDefault_Settings();

    }

    @Override
    public void annotate(GLAutoDrawable drawable) {

    }




}
