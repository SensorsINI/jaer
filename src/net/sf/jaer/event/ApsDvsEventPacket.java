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
 * and you will get just the DVS events. However, if you iterate over <code>ApsDvsEventPacket</code>,
 * then the APS events will be bypassed to an internal output packet of <code>ApsDvsEventPacket</code>
 * called {@link #outputPacket}. This packet is automatically initialized when you call checkOutputPacketEventType(in). 
 * 
 * TODO explain better.
 * 
 * Call 
 * <pre>   
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
    
      /** Constructs a new ApsDvsEventPacket from this one containing {@link #getEventClass()}.
       * This method overrides {@link EventPacket#constructNewPacket() } to construct <code>ApsDvsEventPacket</code> so
       * it is a kind of copy constructor. 
     * @see #setEventClass(java.lang.Class) 
     * @see #setEventClass(java.lang.reflect.Constructor) 
     */
    @Override
    public EventPacket constructNewPacket(){
        ApsDvsEventPacket packet=new ApsDvsEventPacket(getEventClass());
        return packet;
    }
    
   /** Constructs a new empty ApsDvsEventPacket containing <code>eventClass</code>.
     * @param eventClass the EventPacket will be initialized holding this class of events.
     * @see #setEventClass(java.lang.Class) 
     * @see #setEventClass(java.lang.reflect.Constructor) 
     */
    @Override
    public EventPacket constructNewPacket(Class<? extends BasicEvent> eventClass){
        EventPacket packet=new ApsDvsEventPacket(eventClass);
        return packet;
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
        if(getOutputPacket()==null){
            setOutputPacket((ApsDvsEventPacket)constructNewPacket());
        }else{
            getOutputPacket().clear();
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
    
    /** Initializes and returns the default iterator over events of type <E> consisting of the DVS events.
     * This is the default iterator obtained by <code>for(BasicEvent e:in)</code>.
     @return an Iterator only yields DVS events.
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
        int cursorDvs; // this cursor marks the next DVS event

        public InDvsItr() {
            reset();
            if(getOutputPacket()==null) constructNewPacket();
        }

        /** Overrides the default hasNext() method to use cursorDvs.
         * 
         * @return true if there is a (not filteredOut) event left in the packet
         */
        @Override
        public boolean hasNext() {
            if(usingTimeout) {
                return cursorDvs<size&&!timeLimitTimer.isTimedOut();
            } else {
                while(elementData[cursorDvs].isFilteredOut() && cursorDvs<size) {filteredOutCount++; cursorDvs++;} // TODO: can return filteredOut event if it is the last one in the packet
                return cursorDvs<size;
            }
        }
        
        private ApsDvsEvent lastPacketEvent=null; // an event from the previous packet, which supplies a timestamp if none is available fromt his packet

        /** Overrides the default iterator <code>next()</code> to skip over ADC samples and write these
         * to the designated {@link #outputPacket}.
            * @return the next DVS event, or null if there are none
            */
        @Override
        public E next() {
            // after this call, the cursorDVS either points to the next DVS event or we have reached the end of the packet.
            // Therefore when we enter we need to march forward until we get a DVS event. If there are none we return again the last DVS event, to avoid dealing with null events
            // in all event filters that do not deal with null events.
            E output = (E) elementData[cursorDvs++]; // get next element of this packet (TODO guarenteed to be dvs event how?) and advance cursor
            if (outputPacket != null) {
                OutputEventIterator outItr = outputPacket.getOutputIterator(); // and the output iterator for the "outputPacket" (without resetting it)
                while (output.isSampleEvent() && cursorDvs < size) { // while the event is an ADC sample and we are not done with packet
                    // copy the ADC sample to outputPacket
                    E nextOut = (E) outItr.nextOutput();
                    nextOut.copyFrom(output);
                    output = (E) elementData[cursorDvs++]; // point to next event
                }
            }
            if(!output.isSampleEvent()) { // if event is not an ADC sammple then save it for later
                lastPacketEvent=output;
            }else {
                int lastTs=output.timestamp;
                output = (E)lastPacketEvent;
                if(output!=null) output.timestamp=lastTs; // to avoid small nonmonotonic timestamp backward jumps due to APS events that came at end of packet
            }
            return output; // now return the element we obtained at start
        }

        @Override
        public void reset() {
            cursorDvs=0;
            usingTimeout=timeLimitTimer.isEnabled(); // timelimiter only used if timeLimitTimer is enabled but flag to check it it only set on packet reset
            filteredOutCount=0;
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
