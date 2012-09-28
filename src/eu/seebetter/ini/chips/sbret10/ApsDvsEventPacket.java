/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.sbret10;

import java.util.Iterator;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;

/**
 *
 * @author Christian
 */
public class ApsDvsEventPacket<E extends ApsDvsEvent> extends EventPacket{
    
    /** Constructs a new EventPacket filled with the given event class.
    @see net.sf.jaer.event.BasicEvent
     */
    public ApsDvsEventPacket(Class<? extends ApsDvsEvent> eventClass) {
        if(!BasicEvent.class.isAssignableFrom(eventClass)) { //Check if evenClass is a subclass of BasicEvent
            throw new Error("making EventPacket that holds "+eventClass+" but these are not assignable from BasicEvent");
        }
        setEventClass(eventClass);
    }
    
    /** Returns after initializing the iterator over input events.
    @return an iterator that can iterate over the events.
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
    
    /** Initializes and returns the iterator */
    @Override
    public Iterator<E> iterator() {
        return inputIterator();
    }
    
    public class InDvsItr extends InItr{
        int cursor;
        boolean usingTimeout=timeLimitTimer.isEnabled();

        public InDvsItr() {
            reset();
        }

        public boolean hasNext() {
            if(usingTimeout) {
                return cursor<size&&!timeLimitTimer.isTimedOut();
            } else {
                return cursor<size;
            }
        }

        public E next() {
            E output = (E) elementData[cursor++];
            //skip all APS events
            E nextOut = (E) elementData[cursor];
            while(nextOut.isAdcSample() && cursor<size){
                cursor++;
                nextOut = (E) elementData[cursor];
            }
            return output;
        }

        public void reset() {
            cursor=0;
            usingTimeout=timeLimitTimer.isEnabled(); // timelimiter only used if timeLimitTimer is enabled but flag to check it it only set on packet reset
        }

        public void remove() {
            for(int ctr=cursor; ctr<size; ctr++) {
                elementData[cursor-1]=elementData[cursor];
            }
            //go back as we removed a packet
            cursor--;
            size--;
        //throw new UnsupportedOperationException();
        }

        public String toString() {
            return "InputEventIterator cursor="+cursor+" for packet with size="+size;
        }
    }
    
}
