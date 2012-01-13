/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.integrateandfire;

import javax.swing.SwingUtilities;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author tobi
 */
public abstract class Plotter extends javax.swing.JFrame implements Runnable {

    SuperNetFilter F;
    NetworkArray NA;
    Network NN;
    
    int currentNet=0;
    
    abstract void init();           // Update the plots

    abstract void update();         // Update the plots
    
    abstract void refresh();        // Refresh the display from scratch
    
    public void replot()
    {
        if (SwingUtilities.isEventDispatchThread()) {
            this.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(this);
            } catch (Exception ex) {
                F.log.warning(ex.toString());
            }
        }
    }
    
    @Override
    public void run()
    {   update();            
    }
    
    void load(SuperNetFilter F_, NetworkArray NA_)
    {   NA=NA_;
        
        load(F_, NA.N[currentNet]);
        
    }
        
    public void setCurrentNet(int ind)
    {   if (NA!=null)
            NN=NA.N[ind];
        refresh();
    }
    
    void load(SuperNetFilter F_, Network NN_)   // Initialize the plotter on a network
    {   F=F_;
        NN=NN_;
    }    
    
    int latestTimeStamp()
    {   return F.getLastTimestamp();
        
    }
    
    
    
    
}
    
