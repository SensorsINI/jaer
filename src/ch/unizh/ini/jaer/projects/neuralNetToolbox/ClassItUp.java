/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


package ch.unizh.ini.jaer.projects.neuralNetToolbox;

// JAER Stuff
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;

// Java  Stuff
//import java.io.File;
import java.io.FileNotFoundException;

// Swingers
import javax.swing.JOptionPane;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;

// Plotting packaging


// Other Filters

/**
 *
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

    // Plotting Type
    //int disptype=2; // 1: Plot, 2: Bar-Viewer

    //CenterMe C;
    //Sparsify S;
    FilterChain dickChainy;

    Network Net;
    
    //------------------------------------------------------
    // Filter Methods

    // Deal with incoming packet
    @Override public EventPacket<?> filterPacket( EventPacket<?> P){
        
        if(!filterEnabled) return P;
        
        
        int k=0;
        int dim=128;
        //int dim=32;

        

        //EventPacket Pout=dickChainy.filterPacket(P);
        out=P;
        if (Net==null)
        {   //resetFilter();
            return out;
        }
        
        /*
        EventPacket Pt=S.filterPacket(P); // Sparsify Images
        int test=P.getSize();
        EventPacket Pout=C.filterPacket(Pt); // Center Images
        int test2=P.getSize();
        */
        for(Object e:out){ // iterate over the input packet**
            BasicEvent E=(BasicEvent)e; // cast the object to basic event to get timestamp, x and y**
            Net.propagate(dim*E.x+dim-1-E.y,1,E.timestamp); 
            //NN.propagate(dim*E.x/4+dim-1-E.y/4,1);
            k++;
        }

        if (plot!=null) plot.update();
        
        return out;
    }

    // Read the Network File on filter Reset
    @Override public void resetFilter() {

        initFilter();
        /*
       try{
            Net=new Network();
            Net.readfile(); // Read that Saved Neural Network file

            doChoosePlot(); // Setup the Plotter
       }
       catch(FileNotFoundException M){ 
           System.out.println("Somehow we didn't find the file, even though you just selected it.  Figure that one out.");
       }
       catch(Exception E){ // Generally means you clicked cancel.
           Net=null;
           System.out.println("You cancelled.  Click reset to chose a network.");
           filterEnabled=false;
           // System.out.println(E.getMessage());
        }
*/
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
        plot.load(this, Net);
        plot.init();
    }
    

    //------------------------------------------------------
    // Obligatory method overrides
        
    //  Initialize the filter
    public  ClassItUp(AEChip  chip){
        super(chip);

        //CenterMe C=new CenterMe(chip);
        //Sparsify S=new Sparsify(chip);

        // Change defaults of Sparsification filter
        //S.maxFreq=10000;
        //S.polarityPass=-1;

        //S.setFilterEnabled(true);
        //C.setFilterEnabled(true);
        /*
        dickChainy=new FilterChain(chip);
        
        //dickChainy.add(C);
        //dickChainy.add(S);
        
        dickChainy=new FilterChain(chip);
        //NeurSparse S=new NeurSparse(chip);
//        RectangularClusterTracker R=new RectangularClusterTracker(chip);
        ClusterSet C = new ClusterSet(chip);
        //dickChainy.add(S);
        dickChainy.add(C);
        C.setFilterEnabled(true);
        
        setEnclosedFilterChain(dickChainy);

*/


        /*
        filterchainMain = new FilterChain(chip);
    targetDetect=new TargetDetector(chip);
    ballTracker=new RectangularClusterTracker(chip);
    tbox=new Bbox[2];


    filterchainMain.add(targetDetect);
    filterchainMain.add(ballTracker);

    targetDetect.setEnclosed(true,this);
    ballTracker.setEnclosed(true,this);

    setEnclosedFilterChain(filterchainMain);

        */

/*

        this.

        String prop="Properties";
        setPropertyTooltip(prop,"disptype", "Some Property");

*/


    }        
    
    // Nothing
    @Override public void initFilter(){

         try{
            doLoad_Network();
            doChoosePlot(); // Setup the Plotter
       }
        catch(FileNotFoundException M){

        }
         catch(Exception E){
            System.out.println(E.getMessage());
        }
/**/
        
    }
    
    public void doLoad_Network() throws FileNotFoundException, Exception
    {
        Net=new Network();
        Net.readfile(); // Read that Saved Neural Network file
        NN=Net;
        NN.setThresholds(500);
    }
    
    // Nothing
    public ClassItUp getFilterState(){
        return this;
    }



    //------------------------------------------------------


}
