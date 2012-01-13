/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.integrateandfire;


// Java  Stuff
import java.io.File;
import java.io.FileNotFoundException;


import java.util.Scanner;
import java.util.NoSuchElementException;
import javax.swing.*;
import net.sf.jaer.event.OutputEventIterator;

/**
 *
 * @author tobi
 */
public class NetworkArray implements SuperNet{

    /* Network
     * Every Event-Source/Neuron has an integer "address".  When the event source
     * generates an event, it is propagated through the network.
     *
     * */

    //------------------------------------------------------
    // Properties-Network Related
   
    Network[] N;
    
    public NetworkArray(int number)
    {
        N=new Network[number];
    }
    
    //ENeuron[] N;                 // Array of neurons.  N[i] is the ith neuron.
    public int maxdepth=100;    // Maximum depth of propagation - prevents infinite loops in unstable recurrent nets.

    public void propagate(int index, int source, int depth, int timestamp)
    {   N[index].propagate(source, depth, timestamp); 
    }
    
    public void propagate(int index, int source, int depth, int timestamp, OutputEventIterator outItr)
    {   N[index].propagate(source, depth, timestamp, outItr); 
    }
    
    public void stimulate(int index, int dest, float weight, int timestamp, OutputEventIterator outItr)
    {   // Directly stimulate a neuron with a given weight
        N[index].stimulate(dest,weight,timestamp, outItr);
    }

    @Override
    public String networkStatus(){
        return "Network Array with "+N.length+" Networks";
    }
    
    @Override
    public void setThresholds(float thresh)
    {   for (SuperNet n:N)
            n.setThresholds(thresh);    
    }
    
    @Override
    public void setTaus(float tc)
    {   for (SuperNet n:N)
            n.setTaus(tc);
    }
    
    @Override
    public void setSats(float tc)
    {   for (SuperNet n:N)
            n.setSats(tc);    
    }
    
    @Override
    public void setDoubleThresh(boolean v)
    {   for (SuperNet n:N)
            n.setDoubleThresh(v);    
    }
    
    @Override
    public void reset()
    {   for (SuperNet n:N)
            n.reset();    
    }

    
}
