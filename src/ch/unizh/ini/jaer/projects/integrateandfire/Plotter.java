/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.integrateandfire;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 *
 * @author tobi
 */
public abstract class Plotter extends javax.swing.JFrame implements Runnable {

    // SuperNetFilter F;
    NetworkArray NA;
    Network NN;
    int lasttimestamp;
    
    int currentNet=0;
    
    abstract void init();           // Update the plots

    abstract void update(int timestamp);         // Update the plots
    
    abstract void refresh();        // Refresh the display from scratch
    
    public void replot(int timestamp)
    {   lasttimestamp=timestamp;
        if (SwingUtilities.isEventDispatchThread()) {
            this.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(this);
            } catch (Exception ex) {
                System.out.println(ex.toString());
            }
        }
    }
    
    @Override
    public void run()
    {   update(lasttimestamp);            
    }
    
    void load(NetworkArray NA_)
    {   NA=NA_;
        
        load(NA.getNet(currentNet));
        
    }
        
    public void setCurrentNet(int ind)
    {   if (ind==-1) return;
        currentNet=ind;
        if (NA!=null)
            NN=NA.N[ind];
        refresh();
    }
    
    void load(Network NN_)   // Initialize the plotter on a network
    {   //F=F_;
        NN=NN_;
    }    
        
    public static Plotter choosePlotter(NetworkArray Net)
    {       
        
//        if (plot!=null)
//        {   // Thread safety (am I doing it right?)
//            Plotter ptemp=plot;
//            plot=null;
//            ptemp.dispose();
//        }
        
    
        Object[] options = {"Number Display","Unit Probe"};
        int n = JOptionPane.showOptionDialog(null,
            "How you wanna display this?",
            "Hey YOU",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);

        Plotter plot;
        switch (n){

            case 0: // Swing Display for numbers
                plot=new NumberReader();
                break;
            case 1:
                plot=new Probe();
                break;
            default:
                plot=null;
        }
        plot.load(Net);
        plot.init();
        
        return plot;
    }
    
}
    
