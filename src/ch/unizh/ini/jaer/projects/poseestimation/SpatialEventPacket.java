/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.poseestimation;

import java.util.Iterator;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;

/**
 *
 * @author Haza
 */

public class SpatialEventPacket<E extends SpatialEvent> extends EventPacket<E> {
    
    private EventPacket.InItr fullIterator=null;

    /** 
     * Constructor
     * Makes sure that only takes in SpatialEvents or subclasses
     */
    public SpatialEventPacket(Class<? extends SpatialEvent> eventClass) {
        if(!BasicEvent.class.isAssignableFrom(eventClass)) { 
            throw new Error("Making EventPacket that holds "+eventClass+", but these are not assignable from BasicEvent");
        }
        setEventClass(eventClass);
    }
    
    /** 
     * Makes sure next event packet is of correct class 
     * Creates and returns it
     * When does this get called?
     */
    @Override
    public EventPacket getNextPacket(){
        setNextPacket(new SpatialEventPacket(getEventClass()));
        return nextPacket;
    }
    
    /** 
     * Iterator for only DVS events (not spatial events)
     * @return An iterator that can iterate over only DVS Events
     */
    @Override
    public Iterator<E> inputIterator() {
        if(inputIterator==null) {
            inputIterator=new InDvsItr();
        } else {
            inputIterator.reset();
        }
        return inputIterator;
    }
    

    /** 
     * Iterator for all event inputs (DVS and Sensor)
     * @return an iterator that can iterate over the events
     */
    public Iterator<E> fullIterator() {
        if(fullIterator==null) {
            fullIterator=new InItr();
        } else {
            fullIterator.reset();
        }
        return fullIterator;
    }
    
    /** 
     * Initializes and returns the iterator 
     */
    @Override
    public Iterator<E> iterator() {
        return inputIterator();
    }
    
    /** 
     * Class for DVS Input Iterator that ignores Sensor data
     */
    public class InDvsItr extends InItr{

        int cursor;
        boolean usingTimeout=timeLimitTimer.isEnabled();

        public InDvsItr() {
            // Super call resets this
            reset();
        }

        // Don't think I need to overwrite this
        @Override
        public boolean hasNext() {
            if(usingTimeout) {
                return cursor<size&&!timeLimitTimer.isTimedOut();
            } else {
                return cursor<size;
            }
        }

        /** 
         * Retrieve next element
         * Cursor always points to index of next element, so this function retrieves element to which cursor points to 
         * and then increments cursor to next non special event
         */
        @Override
        public E next() {
            E output = (E) elementData[cursor++];
            // Move cursor to next non special (spatial) element or end 
            // While we are not at the end, keep checking until we reach the first non special (spatial) event
            // Use found flag to indicate when non special event is found
            boolean found = false;
            while (cursor < size && found == false) {
                E nextIn = (E) elementData[cursor];
                if (nextIn.special) {
                    cursor++;
                    // Why do we need this??
                    //E nextOut = (E) nextPacket.getOutputIterator().nextOutput();
                    //nextOut.copyFrom(nextIn);
                } else {
                    found = true;
                }
            }
            return output;
        }

        // Don't need to override this
        @Override
        public void reset() {
            cursor=0;
            usingTimeout=timeLimitTimer.isEnabled(); // timelimiter only used if timeLimitTimer is enabled but flag to check it it only set on packet reset
        }

        // Don't need to override this
        @Override
        public void remove() {
            for(int ctr=cursor; ctr<size; ctr++) {
                elementData[cursor-1]=elementData[cursor];
            }
            //go back as we removed a packet
            cursor--;
            size--;
        //throw new UnsupportedOperationException();
        }

        // Don't need to override this
        @Override
        public String toString() {
            return "InputEventIterator cursor="+cursor+" for packet with size="+size;
        }
    }
    
}
