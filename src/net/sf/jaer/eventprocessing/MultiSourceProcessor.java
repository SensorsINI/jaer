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
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.InputEventIterator;
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
    
    private int maxWaitTime=100000; // Maximum time to wait (in microseconds) for events from one source before continuing
    
    boolean[] queueAlive;
    
    /** Initialize a MultiSensoryFilter with the chip, and the number of inputs
     it will take
     */
    public MultiSourceProcessor(AEChip chip,int nInputs) // Why is "chip" so tightly bound with viewing options??
    {   super(chip);        
    
        pq=new PriorityQueue(nInputs,new EventComp());
        
        for (int i=0; i<nInputs; i++)
            buffers.add(new LinkedList());
        
        queueAlive=new boolean[buffers.size()];
        
        out=new EventPacket();
        
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
        
    /** Number of inputs that this filter takes */
    public int nInputs()
    {   return getInputNames().length;
    }
    
    abstract public String[] getInputNames();

    /**
     * @return the maxWaitTime
     */
    public int getMaxWaitTime() {
        return maxWaitTime;
    }

    /**
     * @param maxWaitTime the maxWaitTime to set
     */
    public void setMaxWaitTime(int maxWaitTime) {
        this.maxWaitTime = maxWaitTime;
    }
    
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
     * This method ensures that output events will be written in order, so long
     * as sources are synchronized to within a call-cycle.  It uses
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
//        
//        if (lastEvts==null)
//        {   lastEvts=new ArrayList();
//            for (int i=0; i<buffers.size(); i++)
//                lastEvts.add(new BasicEvent());
//            
//        }
        
        int goToTime=Integer.MIN_VALUE;
        
        try {

            
            
            // Step 1: dump all events into queues
            for (int i = 0; i < packets.size(); i++) {
//                BasicEvent lastEvent=new BasicEvent();
//                if (!buffers.get(i).isEmpty())
//                    lastEvent.copyFrom(((LinkedList<BasicEvent>)buffers.get(i)).peekLast());
                
//                for (int j = 0; j < packets.get(i).getSize(); j++) {
//                    BasicEvent currentEvent = packets.get(i).getEvent(j);
//                    if (currentEvent.timestamp < lastEvts.get(i).timestamp) {
//                        System.out.println("TimeStamp Nonmonotonicity: "+currentEvent.timestamp+" < "+ lastEvts.get(i).timestamp);
//                    }
//                    lastEvts.get(i).copyFrom(currentEvent);
////                    if (!buffers.get(i).isEmpty())
////                        lastEvent.copyFrom(((LinkedList<BasicEvent>)buffers.get(i)).peekLast());
//                }
                               
                
//                Iterator<BasicEvent> itr = packets.get(i).iterator();
//                Iterator<BasicEvent> itr = packets.get(i).inputIterator();
                
                
                BasicEvent ev = null;
                for (int k=0; k<packets.get(i).getSize(); k++)  {
//                    ev = itr.next();                                
                    ev = packets.get(i).getEvent(k);

                    BasicEvent evo = ev.getClass().newInstance(); // This seems WRONG! Alternative would be adding a copy method to all events
                    evo.copyFrom(ev);
                    evo.source = (byte) i;
                    
                    LinkedList<BasicEvent> testBuf=(LinkedList)buffers.get(i);
                    
//                    if(!testBuf.isEmpty() && lastEvts.get(i)!=testBuf.peekLast())
//                    if(ev!=packets.get(i).getEvent(k))  
//                        System.out.println("Sumething funny going on");
                        
                    if (!testBuf.isEmpty() && testBuf.getLast().timestamp>evo.timestamp)
                         System.out.println("NonMonotonicTimeStamp!");
                    
                    
//                    lastEvts.set(i,evo);
                    buffers.get(i).add(evo);
                }

                if (ev!=null)
                    goToTime=Math.max(goToTime, ev.timestamp);
            }

        } catch (InstantiationException ex) {
            Logger.getLogger(MultiSourceProcessor.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(MultiSourceProcessor.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        goToTime-=getMaxWaitTime();
        
        /* Step 2, if dead queues have received events, revive them, add their 
         * elements back into the priority queue.
         */
        if (pq.size()<buffers.size())
        {   for (int i=0; i<buffers.size(); i++)
            {   if (!queueAlive[i] && !buffers.get(i).isEmpty())
                {   BasicEvent ev=buffers.get(i).poll();
                    ev.source=(byte)i;
                    pq.add(ev);
                    queueAlive[i]=true;
                }
            }
//            // If we've gotten here, each buffer has at least one event
//            for (byte i=0; i<buffers.size(); i++)
//            {   BasicEvent ev=buffers.get(i).poll();
//                ev.source=i;
//                pq.add(ev);
//            }
//            // Each source will now have an event in the priority queue
        }
            
        /* Step 3: pull ordered events from priority queue.  Replace each pulled
         * event with an event originating from the same buffer as the pulled 
         * event, ensuring that pq always contains 1 element from each source.  
         * Run until one of the source buffers is empty.
         */
        out.clear();
        OutputEventIterator<BasicEvent> outItr=out.outputIterator();
        
//        String lastEventType="";
        
//        int lastTimestamp=Integer.MIN_VALUE;
        while(!pq.isEmpty())
        {
            
            if (pq.peek().timestamp>goToTime)
                break;
            
            // Pull next output event from head of Priority Queue, write to output
            BasicEvent ev=pq.poll();
                        
            outItr.writeToNextOutput(ev);
//            if (ev.timestamp<lastTimestamp) // Check for monotonicity
//            {   System.out.println("Timestamp decrease detected: ("+lastEventType+"-->"+ev.toString()+")  Resetting event queues!");
//                resynchronize();
//                break;
//            }
//            lastTimestamp=ev.timestamp;
//            lastEventType=ev.toString();
            
            // If last-pulled-from-buffer is empty  
            if (buffers.get(ev.source).isEmpty())
            {   // If buffer did get input this round, return
                if (queueAlive[ev.source])
                {   // Mark the queue as dead, do not replace item polled from Priority Queue, return
                    queueAlive[ev.source]=false;
//                    System.out.println("nA:"+nA+"\t nB:"+nB); 
                    break;
                }
                
            }
            else 
            {   // Replace event polled with event from same buffer
                BasicEvent pulledFromBuffer=buffers.get(ev.source).poll();
                
                pq.add(pulledFromBuffer);
            }
                        
//            if (ev.source==0) nA++; else if (ev.source==1) nB++;
            
        }
        
        // Confirm ordering
        
//        for (int i=1; i<out.getSize(); i++)
//        {
//            if (out.getEvent(i).timestamp<out.getEvent(i-1).timestamp)
//                System.out.println("NON-MONOTONIC TIMESTAMPS!");
//        }
        
        
        
//        out.
        return out;
        
//        while(true)
//        {
//            BasicEvent ev=pq.peek();
//            
//            if (buffers.get(ev.source).isEmpty())
//            {   System.out.println("nA:"+nA+"\t nB:"+nB); 
//                return out;            
//            }
//            
//            outItr.writeToNextOutput(pq.poll());
//                        
//            if (ev.source==0) nA++; else if (ev.source==1) nB++;
//            
//            // If last-pulled-from queue is empty  
//            if (buffers.get(ev.source).isEmpty())
//            {   // If furthermore, this queue was had no input events in this round
//                if (queueAlive[ev.source])
//                {   System.out.println("nA:"+nA+"\t nB:"+nB); 
//                    return out;
//                }
//            }
//            else 
//                
//            BasicEvent pulledFromQueue=buffers.get(ev.source).poll();
//            
//            if (pulledFromQueue.timestamp < ev.timestamp)
//            {
//                System.out.println("Timestamp decrease detected.  Resetting event queues!");
//                resynchronize();
//                return out;
//            }
//            
//            // Replace the last item pulled from the PQ with an item from the same buffer.
//            pq.add(pulledFromQueue);
//        }
        
        
    }
    
    
    public void resynchronize()
    {
        for (int i=0;i<buffers.size();i++)
        {   buffers.get(i).clear();
            queueAlive[i]=false;
        }
        pq.clear();
        
        
    }
    
}
