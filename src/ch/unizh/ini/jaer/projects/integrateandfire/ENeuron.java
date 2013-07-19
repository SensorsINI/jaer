/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author Peter
 */
public class ENeuron extends Neuron{
    // Extension for the common neuron by adding event-based info for it.
    
    short x=0;            // Associated x coordinate
    short y=0;            // Associated y coordinate
    byte type=0;          // Byte identifying map/layer/whatever
    public char tag='x';  // "tag" to label individual neurons
    boolean out=false;    // Is the neuron an output neuron?
    
    public boolean spike(float w,int timestamp,OutputEventIterator outItr){
        // Get current time, calculate Vmem decay, update for this input
        //float now=System.nanoTime()/1000000;
        
        boolean didit=super.spike(w,timestamp);
        if (out && didit){
            TypedEvent e=(TypedEvent)outItr.nextOutput();             
            e.x=x;
            e.y=y;
            e.type=type;
            e.timestamp=timestamp;
        }
        return didit;
    }
}
