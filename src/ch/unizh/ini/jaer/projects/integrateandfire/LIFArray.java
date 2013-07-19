/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.integrateandfire;


// Java  Stuff
import net.sf.jaer.event.OutputEventIterator;

/**
 *
 * @author tobi
 */
public class LIFArray extends NetworkArray implements LIFcontroller{

    /* Network
     * Every Event-Source/Neuron has an integer "address".  When the event source
     * generates an event, it is propagated through the network.
     *
     * */

    //------------------------------------------------------
    // Properties-Network Related
    LIFNet[] lif;
    
    public LIFArray(int n) throws Exception
    {   
        super(LIFNet.class,n);        
    }
    
    public void setNetCount(int number)
    {   lif=new LIFNet[number];
        N=lif;
    }
    
    //ENeuron[] N;                 // Array of neurons.  N[i] is the ith neuron.
    public int maxdepth=100;    // Maximum depth of propagation - prevents infinite loops in unstable recurrent nets.

    
    public void propagate(int index, int source, int depth, int timestamp)
    {   lif[index].propagate(source, depth, timestamp); 
    }
    
    public void propagate(int index, int source, int depth, int timestamp, OutputEventIterator outItr)
    {   lif[index].propagate(source, depth, timestamp, outItr); 
    }
    
    public void stimulate(int index, int dest, float weight, int timestamp, OutputEventIterator outItr)
    {   // Directly stimulate a neuron with a given weight
        lif[index].stimulate(dest,weight,timestamp, outItr);
    }

    @Override
    public String networkStatus(){
        return "Network Array with "+N.length+" Networks";
    }
    
    @Override
    public void setThresholds(float thresh)
    {   for (LIFcontroller n:lif)
            n.setThresholds(thresh);    
    }
    
    @Override
    public void setTaus(float tc)
    {   for (LIFcontroller n:lif)
            n.setTaus(tc);
    }
    
    @Override
    public void setSats(float tc)
    {   for (LIFcontroller n:lif)
            n.setSats(tc);    
    }
    
    @Override
    public void setDoubleThresh(boolean v)
    {   for (LIFcontroller n:lif)
            n.setDoubleThresh(v);    
    }
    
    @Override
    public void reset()
    {   for (LIFcontroller n:lif)
            n.reset();    
    }

    
}
