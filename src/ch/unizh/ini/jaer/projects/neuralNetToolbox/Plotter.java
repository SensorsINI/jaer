/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.neuralNetToolbox;

import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author tobi
 */
public abstract class Plotter extends javax.swing.JFrame {

    SuperNetFilter F;
    Network NN;
    
    abstract void init();          // Update the plots

    abstract void update();          // Update the plots
    
    void load(SuperNetFilter F_, Network NN_)   // Initialize the plotter on a network
    {   F=F_;
        NN=NN_;
    }    
    
    int latestTimeStamp()
    {   return F.getLastTimestamp();
        
    }
}
    
