/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;

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
    
    public int lastEventTime=Integer.MIN_VALUE;
    
    boolean liveSources = false;
    
    // Keep track of desynchronized, possibly looping time sources
    int[] bufferLoopOffsets;
    int[] bufferStarts;
    int[] bufferPrevTimes;
    
    /** Initialize a MultiSensoryFilter with the chip, and the number of inputs
     it will take
     */
    public MultiSourceProcessor(AEChip chip) // Why is "chip" so tightly bound with viewing options??
    {   super(chip);        
    
        int nInputs=getInputNames().length;
    
        if (nInputs==0)
            nInputs=1;
    
        pq=new PriorityQueue(nInputs,new EventComp());
        
        for (int i=0; i<nInputs; i++)
            buffers.add(new LinkedList());
        
        queueAlive=new boolean[buffers.size()];
        bufferStarts = new int[buffers.size()];
        bufferPrevTimes = new int[buffers.size()];
        
        // Ensure proper comparison
        Arrays.fill(bufferStarts,Integer.MIN_VALUE);
        
        out=new EventPacket();
        
    }
    
//    abstract public void filterPacket(ArrayList<EventPacket> packets,int[] order);
    
//    abstract public String[] getInputLabels();
    
    public EventPacket filterPackets(ArrayList<EventPacket> packets)
    {   
        return filterPacket(mergePackets(packets));
    }
    
    
//    public void addDisplayWriter(DisplayWriter disp)
//    {
//        this.getChip().getAeViewer().getJaerViewer().globalViewer.addDisplayWriter(disp);
//    }
    
    
        
    
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

        if (packets.size()==1)
            return packets.get(0);

        int goToTime=Integer.MIN_VALUE;        
        
        try { 
            // Step 1: dump all events into queues
            for (int i = 0; i < packets.size(); i++) {                                
                BasicEvent ev = null;
                
                // Skip uninitialized sources
                if(packets.get(i)==null)
                    continue;
                
                for (int k=0; k<packets.get(i).getSize(); k++)  {
                    ev = packets.get(i).getEvent(k);
                    
                    if(bufferStarts[i] == Integer.MIN_VALUE)
                        bufferStarts[i] = ev.timestamp;
                    
                    ev.timestamp -= bufferStarts[i];
                    
                    /*
                    // Find true start time, so that times run from zero to +inf
                    if(bufferStarts[i] == Integer.MIN_VALUE)
                        bufferStarts[i] = ev.timestamp;
                    int preadjust = ev.timestamp;                    
                    // Subtract off start time and add loop offset time
                    ev.timestamp = ev.timestamp - bufferStarts[i] + bufferLoopOffsets[i];
                    
                    // If we've looped in time, continue adding time
                    if(bufferPrevTimes[i] > ev.timestamp){
                        // If we somehow looped back before a time we've seen,
                        //   create a new artificial start time that keeps it all
                        //   sane and monotonic and correct.
                        if(ev.timestamp < 0){
                            bufferStarts[i] = preadjust + bufferLoopOffsets[i] - 
                                    bufferPrevTimes[i] - 1;
                            ev.timestamp = preadjust - bufferStarts[i] + bufferLoopOffsets[i];
                        }
                        else {
                            bufferLoopOffsets[i] = bufferPrevTimes[i]+1;
                            ev.timestamp += bufferLoopOffsets[i];
                        }
                    }
                    
                    
                    if (ev.timestamp < 0)
                        throw new RuntimeException("Event found below initial timestamp. Time:" +
                                ev.timestamp);
                    
                    // Store this time for next iteration
                    bufferPrevTimes[i]=ev.timestamp;
                    */
                    
                    
                    if (ev.timestamp < 0)
                        throw new RuntimeException("Event found below initial timestamp. Time:" +
                                ev.timestamp);
                    
                    bufferPrevTimes[i] = ev.timestamp;
                    
                    BasicEvent evo = ev.getClass().newInstance(); // This seems WRONG! Alternative would be adding a copy method to all events
                    evo.copyFrom(ev);
                    evo.source = (byte) i;
                    
                    LinkedList<BasicEvent> testBuf=(LinkedList)buffers.get(i);
                    
                    //if (!testBuf.isEmpty() && testBuf.getLast().timestamp>evo.timestamp)
                    //     System.out.println("NonMonotonicTimeStamp!");
                    
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

                    /*
                    if (lastEventTime!=Integer.MIN_VALUE && ev.timestamp-lastEventTime<0)
                    {   
                        resynchronize();      
                        throw new RuntimeException("The event-streams from your sources are out of synch. "
                                + "by "+(ev.timestamp-lastEventTime)/1000+"ms, which is more than the max wait time of "+
                                maxWaitTime/1000+"ms.  Either synchronize your sources or set a bigger maxWaitTime.");
                    }
                    */
                    
                    pq.add(ev);
           
                    queueAlive[i]=true;
                }
            }
        }
                
            
        /* Step 3: pull ordered events from priority queue.  Replace each pulled
         * event with an event originating from the same buffer as the pulled 
         * event, ensuring that pq always contains 1 element from each source.  
         * Run until one of the source buffers is empty.
         */
        if (out==null)// Why does this happen?
            out=new EventPacket();
        out.clear();
        OutputEventIterator<BasicEvent> outItr=out.outputIterator();
        
        BasicEvent ev=null;
        while(!pq.isEmpty())
        {
            
            if (pq.peek().timestamp>goToTime)
                break;
            
            // Pull next output event from head of Priority Queue, write to output
            ev=pq.poll();
                        
            outItr.writeToNextOutput(ev);
            
            // If last-pulled-from-buffer is empty  
            if (buffers.get(ev.source).isEmpty())
            {   // If buffer did get input this round, return
                if (queueAlive[ev.source])
                {   // Mark the queue as dead, do not replace item polled from Priority Queue, return
                    queueAlive[ev.source]=false;
                    break;
                }
                
            }
            else 
            {
                // Replace event polled with event from same buffer
                BasicEvent pulledFromBuffer=buffers.get(ev.source).poll();
                
                pq.add(pulledFromBuffer);
            }
        }
        
        if (ev!=null)
            lastEventTime=ev.timestamp;
        
        return out;
        
    }
    
    
    public void resynchronize()
    {
        lastEventTime=Integer.MIN_VALUE;
        for (int i=0;i<buffers.size();i++)
        {   
            bufferStarts[i] = Integer.MIN_VALUE;
            buffers.get(i).clear();
            queueAlive[i]=false;
        }
        pq.clear();
    }
}
