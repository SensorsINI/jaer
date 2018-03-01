/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


package ch.unizh.ini.jaer.projects.integrateandfire;

// JAER Stuff
import com.jogamp.opengl.GLAutoDrawable;
import java.io.File;
// Java  Stuff
import java.io.FileNotFoundException;
// Swingers
import java.util.Arrays;

import javax.swing.JOptionPane;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;

// Plotting packaging


// Other Filters

/**
 * @description Attempts to classify digits
 * @author Peter
 */
public class LIFNetFilt extends SuperLIFFilter implements FrameAnnotater {
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

    LIFArray Net;

    int current; // Index of current network

    //HashMap H;
    //ArrayList<ClusterSet.Cluster> clusterMapping;   // Mapping of cluster numbers to network indeces
    //ArrayList<Integer> clusterMapping;

    ClusterSet C;  // Link to clustering filter

    int netCount=4;

    File genFile; // File that we read in to generate networks

    //==========================================================================
    // Filter Methods

    // Deal with incoming packet
    @Override public EventPacket<?> filterPacket( EventPacket<?> in){

        if(!filterEnabled) {
			return in;
		}

        int k=0;
        int dim=128;
        //int dim=32;

        //EventPacket Pout=dickChainy.filterPacket(P);


        if (enclosedFilterChain!=null) {
			in=enclosedFilterChain.filterPacket(in);
		}

        if ((Net==null) || !enableNetwork)
        {   out=in;
            return out;
        }

        out=in;
        if (out.getEventClass() == ClusterEvent.class)
        {
            // Iterate through events, send them through the network
            for(Object e:out)
            { // iterate over the input packet**
                ClusterEvent E=(ClusterEvent)e; // cast the object to basic event to get timestamp, x and y**
                int index=E.getCluster().index;

                if (index==-1)
                {   System.out.println("Unaddressed cluster.  This shouldn't happen but hey look, it did big woop.");
                    continue;
                }
                Net.propagate(index,((dim*E.xp)+dim)-1-E.yp,1,E.timestamp);
            }
        }
        else
        {
            for(Object e:out)
            { // iterate over the input packet**
                BasicEvent E=(BasicEvent)e; // cast the object to basic event to get timestamp, x and y**
                Net.propagate(0,((dim*E.x)+dim)-1-E.y,1,E.timestamp);
            }

        }


        /*
        EventPacket Pt=S.filterPacket(P); // Sparsify Images
        int test=P.getSize();
        EventPacket Pout=C.filterPacket(Pt); // Center Images
        int test2=P.getSize();
        */
        setClusterTags();

        if (plot!=null)
        {   plot.replot(this.getLastTimestamp());
        }

        return out;
    }


    // Read the Network File on filter Reset
    @Override public void resetFilter() {
        initFilter();
    }

    //------------------------------------------------------
    // Output-Generating Methods

    public void doChoosePlot(){

        if (plot!=null)
        {   // Thread safety (am I doing it right?)
            Plotter ptemp=plot;
            plot=null;
            ptemp.dispose();
        }

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
                plot=new NumberReader();
                break;
            case 2:
                plot=new Probe();
                break;
        }
        plot.load(Net);
        plot.init();
    }


    //------------------------------------------------------
    // Obligatory method overrides

    //  Initialize the filter
    public  LIFNetFilt(AEChip  chip){
        super(chip);

        FilterChain dickChainy=new FilterChain(chip);

        // Neuron Map Filter for sparsifying input (saving cycles!)
        //NeuronMapFilter N=new NeuronMapFilter(chip);
        //N.setFilterEnabled(false);
        //N.initFilter();

        // Clustering Filter
        C=new ClusterSet(chip);

        dickChainy.add(new PreProcess(chip));
        //dickChainy.add(N);
        dickChainy.add(C);

        setEnclosedFilterChain(dickChainy);

        setPropertyTooltip("Network","netCount", "Number of networks to use in parallel: Will also change the number of clusters");

        putString("Status","Great!");
    }

    // Nothing
    @Override public void initFilter(){
        setAnnotationEnabled(true);

        enclosedFilterChain.reset();
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
    {
        setEnableNetwork(false);

        genFile=null;
        Net=new LIFArray(0);
        setNetCount(4); // File's read in here
        Net.setThresholds(500);
        NN=Net;

        // Stop Top layer from firing!
        int i;
        for (LIFNet nn:(Net.lif)) {
			for (i=1; i<11; i++) {
				nn.N[nn.N.length-i].thresh=100000;
			}
		}

        modifyNetworkStatus(Net.networkStatus());

        setEnableNetwork(true);
    }

    public File getGenFile()  throws FileNotFoundException, Exception
    {   if (genFile==null)
        {   // Get file from the default directory
            File dir=new File(getClass().getClassLoader().getResource(".").getPath().replaceAll("%20", " ")+"filterSettings/NeuralNets/");
            genFile=Network.getfile(dir);
            return genFile;
        }
	else {
		return genFile;
	}
    }

    public void setNetCount(int count) throws FileNotFoundException, Exception
    {
        this.filterEnabled=false;
        C.setMaxNumClusters(netCount);
        if (count < Net.N.length) {
			Net.N=Arrays.copyOfRange(Net.N, 0, count-1);
		}
		else
        {   LIFNet[] nn=new LIFNet[count];
            for (int i=0; i<(count-Net.N.length); i++)
             {   if (i<Net.N.length) {
				nn[i]=Net.lif[i];
			}
			else
                 {  nn[i]=new LIFNet();
                    nn[i].readfile(getGenFile());
                 }

            }
            Net.N=nn;
        }

        netCount=count;
        tagNetworks();
        this.filterEnabled=true;
    }

    public LIFNet getCurrentNet()
    {   return Net.lif[current];
    }

    private void tagNetworks()
    {   // Tags the neurons with output labels.
        for (LIFNet net:Net.lif) {
			for (int i=0; i<10; i++) {
				net.N[(net.N.length-10)+i].tag=(char)('0'+i);
			}
		}
    }


    public void setClusterTags()
    {   if (C==null) {
		return;
	}

        for (ClusterSet.Cluster c:C.getClusters()) {
			((ClusterSet.ExtCluster)c).tag="["+getWinnerTag(Net.lif[((ClusterSet.ExtCluster)c).index])+"?]";
		}

    }

    public char getWinnerTag(LIFNet netnet)
    {   float vmax=-100000, vout;
        int i,imax=0;
        for (i=netnet.N.length-10; i<netnet.N.length; i++)
        {   vout=netnet.N[i].get_vmem(getLastTimestamp());
            if (vout>vmax) {vmax=vout; imax=i;}
        }
        return netnet.N[imax].tag;
    }


    // Nothing
    public LIFNetFilt getFilterState(){
        return this;
    }



    //------------------------------------------------------

    @Override
    public void annotate(GLAutoDrawable drawable) {

    }
/**/

}
