/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.graphics.DisplayWriter;

/**
 * This is an extension of EventFilter2D that can deal with multiple streams of 
 * input packets, for example from two different sensors.
 * @author Peter
 */
public abstract class MultiSourceProcessor extends EventFilter2D {
    
    
    ArrayList<Queue<BasicEvent>> buffers=new ArrayList();   // Stores events to ensure monotonicity between calls.
    
    PriorityQueue<BasicEvent> pq;
    
    int maxWaitTime=100000; // Maximum time to wait (in microseconds) for events from one source before continuing
    
    /** Initialize a MultiSensoryFilter with the chip, and the number of inputs
     it will take
     */
    public MultiSourceProcessor(AEChip chip,int nInputs) // Why is "chip" so tightly bound with viewing options??
    {   super(chip);        
    
        pq=new PriorityQueue(nInputs,new EventComp());
        
        for (int i=0; i<nInputs; i++)
            buffers.add(new LinkedList());
    }
    
//    abstract public void filterPacket(ArrayList<EventPacket> packets,int[] order);
    
//    abstract public String[] getInputLabels();
    
    public EventPacket filterPackets(ArrayList<EventPacket> packets)
    {   
        return filterPacket(mergePackets(packets));
    }
    
    
    public void addDisplayWriter(DisplayWriter disp)
    {
        this.getChip().getAeViewer().getJaerViewer().globalViewer.addDisplayWriter(disp);
    }
    
    
    
    
//    /** return an Enumerator indicating the list of inputs to the filter.
//     *  
//     * @TODO  determine if this should be something more dynamic in future
//     * @return 
//     */
//    abstract public Enum getInoputSurces();
    
//    /**
//     * Order Events by timestamp.  This returns an array of indeces which show 
//     * the order in which packets should be sorted.
//     * @param packets
//     * @deprecated 
//     * @return 
//     */
//    public static int[] orderEvents(ArrayList<EventPacket> packets) {   // Quick and dirty, inefficient solution
//
//        // A better solution would use a Priority queue and maybe some kind of sourced event
//        // Right now this contains a quick and dirty solution to work with 1 or 2 inputs
//
//        if (packets.size() == 1) {
//            
//            return new int[packets.get(0).getSize()];
//            
//        } else if (packets.size() == 2) {
//            int[] arr = new int[packets.get(0).getSize() + packets.get(1).getSize()];
//
//            int j = 0, k = 0;
//            EventPacket p0 = packets.get(0);
//            EventPacket p1 = packets.get(1);
//            int p1size = p1.getSize();
//
//            for (int i = 0; i < arr.length; i++) {
//                if (k > p1size || p0.getEvent(j).timestamp < p1.getEvent(k).timestamp) {
//                    arr[i] = j++;
//                } else {
//                    arr[i] = k++;
//                }
//            }
//            return arr;
//        } else {
//            throw new UnsupportedOperationException("YOU GAVE US " +packets.size()+", BUT WE ONLY CURRENTLY HANDLE 1 or 2!!");
//            //return new int[0];
//        }
//
//
//    }
    
    /** Number of inputs that this filter takes */
    public int nInputs()
    {   return getInputNames().length;
    }
    
    abstract public String[] getInputNames();
    
    /** Comparator class for events, compares by timestamp */
    class EventComp implements Comparator<BasicEvent>
    {   @Override
        public int compare(BasicEvent o1, BasicEvent o2) {
            return o1.getTimestamp()-o2.getTimestamp(); // Compare might be more efficient.
        }

    }
    
    /** Take in a set of EventPackets and merge them into a single packet, 
     * writing their index in the input list into the source bits of the events.
     * 
     * This method ensures that output events will be written in order.  It uses
     * internal queues to take care of the following case:
     * 
     * Call 1:
     * Source 1 produces a packet ending at t=1;
     * Source 2 produces a packet ending at t=3;
     * 
     * Call 2:
     * Source 1 produces a packet starting at t=2;
     * 
     *  
     * @return 
     */
    EventPacket mergePackets(ArrayList<EventPacket> packets)
    {
        /*
         * packet 0:    ooooo \
         * packet 1:  ooooooo - pq --> oooooooooooooooo     : output packet
         * packet 2:     oooo /
         */
        
        
        
        // Step 1: dump all events into queues
        for (int i=0; i<packets.size(); i++)
        {   Iterator<BasicEvent> itr=packets.get(i).iterator();
            
            while (itr.hasNext())
                buffers.get(i).add(itr.next());
        }
        
        /* Step 2, if queue is empty, fill it with the first event from each buffer.
         * This should only happen once, before each queue has recieved a single 
         * input event from each source.  First we must assert that each buffer has
         * at least one event.  Otherwise we return.
         */
        if (pq.isEmpty())
        {   for (Queue buf:buffers)
            {   if (buf.isEmpty())
                    return out;
            }
            // If we've gotten here, each buffer has at least one event
            for (byte i=0; i<buffers.size(); i++)
            {   BasicEvent ev=buffers.get(i).poll();
                ev.source=i;
                pq.add(ev);
            }
            // Each source will now have an event in the priority queue
        }
            
        
        /* Step 3: pull ordered events from priority queue.  Replace each pulled
         * event with an event originating from the same buffer as the pulled 
         * event, ensuring that pq always contains 1 element from each source.  
         * Run until one of the source buffers is empty.
         */
        OutputEventIterator<BasicEvent> outItr=out.outputIterator();
        while(true)
        {
            BasicEvent ev=pq.peek();
            
            if (buffers.get(ev.source).isEmpty())
                return out;
            
            ev=outItr.nextOutput();
            ev.copyFrom(pq.poll());
                        
            pq.add(buffers.get(ev.source).poll());
        }
        
        
    }
    
}
