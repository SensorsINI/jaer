/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.event;

import java.util.Iterator;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;

/**
 * A "cooked" event packet containing ApsDvsEvent. 
 * 
 * <p>
 * This subclass of EventPacket allows iteration of just the events disregarding the 
 * image sensor samples. To use this type of packet for processing DVS events, 
 * you can just use the default iterator (i.e., for(BasicEvent e:inputPacket) 
 * and you will get just the DVS events. However you *must* remember to call 
 * checkOutputPacketType or else all your memory will be quickly consumed. 
 * 
 * TODO explain better.
 * 
 * Call 
 *      <pre>   
 * checkOutputPacketEventType(in); 
 * </pre>
 * before your iterator
 * <p>
 * Internally this class overrides the default iterator of EventPacket.
 * 
 * @author Christian
 * @see BackgroundActivityFilter
 */
public class ApsDvsEventPacket<E extends ApsDvsEvent> extends EventPacket<E>{
    
    /** Constructs a new EventPacket filled with the given event class.
    @see net.sf.jaer.event.BasicEvent
     */
    public ApsDvsEventPacket(Class<? extends ApsDvsEvent> eventClass) {
        if(!BasicEvent.class.isAssignableFrom(eventClass)) { //Check if evenClass is a subclass of BasicEvent
            throw new Error("making EventPacket that holds "+eventClass+" but these are not assignable from BasicEvent");
        }
        setEventClass(eventClass);
    }
    
    /** 
     *  This method constructs and returns the "next" packet, whatever that is TODO explain please 
     * @return 
     */
    @Override
    public EventPacket getNextPacket(){
        setNextPacket(new ApsDvsEventPacket(getEventClass()));
        return nextPacket;
    }
    
    /**This iterator (the default) just iterates over DVS events in the packet.
     * Returns after initializing the iterator over input events.
     * 
    @return an iterator that can iterate over the DVS events.
     */
    @Override
    public Iterator<E> inputIterator() {
        if(inputIterator==null) {
            inputIterator=new InDvsItr();
        } else {
            inputIterator.reset();
        }
        return (Iterator<E>)inputIterator;
    }
    
    private EventPacket.InItr fullIterator=null;
    
    /** This iterator iterates over all events, DVS and APS. Use this one if you want all data.
     * Returns after initializing the iterator over input events.
    @return an iterator that can iterate over all the events, DVS and APS.
     */
    public Iterator<E> fullIterator() {
        if(fullIterator==null) {
            fullIterator=new InItr();
        } else {
            fullIterator.reset();
        }
        return fullIterator;
    }
    
    /** Initializes and returns an iterator over events of type <E> consisting of the DVS events.
     @return an Iterator
     */
    @Override
    public Iterator<E> iterator() {
        return inputIterator();
    }
    
    /** This iterator iterates over DVS events by copying 
     * only them to a reused temporary buffer "output" 
     * packet that is returned and is used for iteration. 
     * Remember to call checkOutputPacket or memory will be quickly consumed. See BackgroundActivityFilter for example.
     * 
     */
    public class InDvsItr extends InItr{
        int cursorDvs;
        boolean usingTimeout=timeLimitTimer.isEnabled();

        public InDvsItr() {
            reset();
        }

        public boolean hasNext() {
            if(usingTimeout) {
                return cursorDvs<size&&!timeLimitTimer.isTimedOut();
            } else {
                return cursorDvs<size;
            }
        }

        public E next() {
            E output = (E) elementData[cursorDvs++]; // get next element of this packet (guarenteed to be dvs event how?) and advance cursor
            //bypass APS events
            E nextIn = (E) elementData[cursorDvs]; // get the next element
            OutputEventIterator outItr=nextPacket.getOutputIterator(); // and the output iterator for the "nextPacket"
            while(nextIn.isAdcSample() && cursorDvs<size){ // while the event is an ADC sample and we are not done with packet
                if (nextPacket != null) {
                    // copy the ADC sample to nextPacket
                    E nextOut = (E) outItr.nextOutput();  
                    nextOut.copyFrom(nextIn);
                }
                cursorDvs++;
                nextIn = (E) elementData[cursorDvs]; // point to next event
            }
            return output; // now return the element we obtained at start
        }

        public void reset() {
            cursorDvs=0;
            usingTimeout=timeLimitTimer.isEnabled(); // timelimiter only used if timeLimitTimer is enabled but flag to check it it only set on packet reset
        }

        public void remove() {
            for(int ctr=cursorDvs; ctr<size; ctr++) {
                elementData[cursorDvs-1]=elementData[cursorDvs];
            }
            //go back as we removed a packet
            cursorDvs--;
            size--;
        //throw new UnsupportedOperationException();
        }

        public String toString() {
            return "InputEventIterator cursor="+cursorDvs+" for packet with size="+size;
        }
    }
    
}
