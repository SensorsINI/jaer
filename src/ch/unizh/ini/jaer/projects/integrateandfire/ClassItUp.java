/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


package ch.unizh.ini.jaer.projects.integrateandfire;

// JAER Stuff
import java.io.File;
import java.util.Iterator;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;

// Java  Stuff
//import java.io.File;
import java.io.FileNotFoundException;

// Swingers
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.swing.JOptionPane;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;

// Plotting packaging


// Other Filters

/**
 * @description Attempts to classify digits
 * @author Peter
 */
public class ClassItUp extends SuperNetFilter {
    /* Network
     * Every Event-Source/Neuron has an integer "address".  When the event source
     * generates an event, it is propagated through the network.  
     * 
     * */

    //------------------------------------------------------
    // Properties-Network Related

    // Bar-Plot Handle
    Plotter plot;       // Numberreader object

    //int viewingIndex;   // Index of network to view through plotter
    
    // Plotting Type
    //int disptype=2; // 1: Plot, 2: Bar-Viewer

    //CenterMe C;
    //Sparsify S;
    //FilterChain dickChainy;

    NetworkArray Net;
    
    int current; // Index of current network
    
    //HashMap H;
    //ArrayList<ClusterSet.Cluster> clusterMapping;   // Mapping of cluster numbers to network indeces
    ArrayList<Integer> clusterMapping;
    
    ClusterSet C;  // Link to clustering filter
    
    int netCount=4;
    
    File genFile; // File that we read in to generate networks
    
    //==========================================================================
    // Filter Methods

    // Deal with incoming packet
    @Override public EventPacket<?> filterPacket( EventPacket<?> in){
        
        if(!filterEnabled) return in;
        
        int k=0;
        int dim=128;
        //int dim=32;

        //EventPacket Pout=dickChainy.filterPacket(P);
        out=enclosedFilterChain.filterPacket(in);
        
        if (Net==null)
        {   return out;
        }
                
        
        
        /*
        if (enclosedFilter != null) {
            out = enclosedFilter.filterPacket(out);
        }
        */
        
        if (out.getEventClass() == ClusterEvent.class)
        {   //updateMapping(((ClusterSet)super.enclosedFilter).getClusters());
            for(Object e:out)
            { // iterate over the input packet**
                
                ClusterEvent E=(ClusterEvent)e; // cast the object to basic event to get timestamp, x and y**
                int index=clusterMapping.indexOf(E.getCluster());
                Net.propagate(index,dim*E.x+dim-1-E.y,1,E.timestamp); 
            }
        }            
        else
        {
            for(Object e:out)
            { // iterate over the input packet**
                BasicEvent E=(BasicEvent)e; // cast the object to basic event to get timestamp, x and y**
                Net.propagate(0,dim*E.x+dim-1-E.y,1,E.timestamp); 
            }
            
        }
        
        
        /*
        EventPacket Pt=S.filterPacket(P); // Sparsify Images
        int test=P.getSize();
        EventPacket Pout=C.filterPacket(Pt); // Center Images
        int test2=P.getSize();
        */
        

        if (plot!=null) plot.update();
        
        return out;
    }
    
    
    public void initMapping()
    {   //clusterMapping=Collections.nCopies(Net.N.length, null);
        clusterMapping=new ArrayList<Integer>();
    }
    
    void updateMapping(List<ClusterSet.Cluster> clusterList)
    {   // Update the mapping from list index to Network index
        // Make a list of the hash maps
        ArrayList clist=new ArrayList<Integer>();
        //for (ClusterSet.Cluster c:clusterList)
        //    clist.add(c.hashCode());
        //Iterator<Cluster> it = clusterList.iterator();
        
        // Purge the clusters that no longer exist
        for (ClusterSet.Cluster c:clusterList)
            if (!clusterMapping.contains(c.hashCode()))
            {    clusterMapping.add(c.hashCode());
                clist.add(c.hashCode());
            } 
            
        for (int i=0;i<clusterMapping.size(); i++)
            if (!clist.contains(clusterMapping.get(i)))
                clusterMapping.remove(clusterMapping.get(i));
                
                   /* 
        for (ClusterSet.Cluster c:clusterMapping)
            if (!clusterList.contains(c))
                clusterMapping.remove(c);
                //c=null;
        
        // Add the new clusters
        for (ClusterSet.Cluster c:clusterList)
            if (!clusterMapping.contains(c))
                clusterMapping.add(c);
                //else
                //    clusterMapping.set(clusterMapping.indexOf(null),c);
               */
    }

    // Read the Network File on filter Reset
    @Override public void resetFilter() {
        initFilter();
    }

    //------------------------------------------------------
    // Output-Generating Methods

    public void doChoosePlot(){

        Object[] options = {"LivePlotter","Number Display","Unit Probe"};
        int n = JOptionPane.showOptionDialog(null,
            "How you wanna display this?",
            "Hey YOU",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[1]);

        switch (n){

            case 0: // LivePlot Display
                plot=new LivePlotter();
                break;
            case 1: // Swing Display for numbers
                plot=new NumberPlot();
                break;
            case 2:
                plot=new UnitProbe();
                break;
        }
        plot.load(this, Net.N[current]);
        plot.init();
    }
    

    //------------------------------------------------------
    // Obligatory method overrides
        
    //  Initialize the filter
    public  ClassItUp(AEChip  chip){
        super(chip);
        
        enclosedFilterChain.add(new NeuronMapFilter(chip));
        
        
        C=new ClusterSet(chip);
        enclosedFilterChain.add(C);
    }        
    
    // Nothing
    @Override public void initFilter(){
//         try{
//            doLoad_Network();
//            doChoosePlot(); // Setup the Plotter
//       }
//        catch(FileNotFoundException M){
//
//        }
//         catch(Exception E){
//            System.out.println(E.getMessage());
//        }        
    }
    
    public int getNetCount(){
        // Number of networks to add
        return this.netCount;
        /*
        if (enclosedFilter!=null && enclosedFilter.isFilterEnabled() && enclosedFilter instanceof ClusterSet)
            return ((ClusterSet)enclosedFilter).getMaxMaxNumClusters();
        else
            return 1;*/
    }
    
    public void doLoad_Network() throws FileNotFoundException, Exception
    {   genFile=null;
        Net=new NetworkArray(0);
        /*
        genFile=null;
        getGenFile();
        
        for (Network n:Net.N)
        {   n=new Network();
            n.readfile(genFile); /// Ugh cloning would be much quicker
        }
                           
        //Net.readfile(); // Read that Saved Neural Network file
        NN=Net;*/
        setNetCount(4);
        NN.setThresholds(500);
    }
    
    public File getGenFile()  throws FileNotFoundException, Exception
    {   if (genFile==null)
        {   genFile=Network.getfile();
            return genFile;
        }
        else return genFile;
    }
    
    public void setNetCount(int count) throws FileNotFoundException, Exception
    {   C.setMaxNumClusters(netCount);
        if (count < Net.N.length)
            Net.N=Arrays.copyOfRange(Net.N, 0, count-1);
        else
        {   Network[] nn=new Network[count];
            for (int i=0; i<count-Net.N.length; i++)
             {   if (i<Net.N.length)
                     nn[i]=Net.N[i];
                 else
                 {  nn[i]=new Network();
                    nn[i].readfile(getGenFile());
                 }
                 
            }
            Net.N=nn;
        }
        initMapping();
        
        
        netCount=count;
    }
    
    
    // Nothing
    public ClassItUp getFilterState(){
        return this;
    }



    //------------------------------------------------------


}
