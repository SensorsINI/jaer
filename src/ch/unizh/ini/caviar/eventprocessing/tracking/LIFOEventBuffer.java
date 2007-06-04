package ch.unizh.ini.caviar.eventprocessing.tracking;

import ch.unizh.ini.caviar.event.BasicEvent;
import java.util.Iterator;


/** A buffer of events with methods for adding new events and getting back old ones in the order of addition, last in is first out.
 *@author tobi delbruck
 */
public class LIFOEventBuffer implements Iterable<BasicEvent> {
    private int length = 100;
    private BasicEvent[] array;
    private int nextIn = 0;
    private int size = 0;
    private Itr itr = null;
    
    /** Make a new instance of LIFOEventBuffer 
     *@param length the number of events to hold
     */
    public LIFOEventBuffer(int length){
        this.length=length;
        array = new BasicEvent[length];
    }
    
    /** Adds an event to the end of the list. The iterator will iterate over the list, starting with this most-recently addeed event.
     *@param e the event
     */
    public void add(BasicEvent e){
        size++;
        array[nextIn]=e;
        nextIn++;
        if(nextIn>=length) nextIn=0;
        if(size>=length) size=length;
    }
    
    /** Resets the pointers and empties the size */
    public void clear(){
        nextIn=0;
        size=0;
    }
    
    /** Returns the number of events presently stored.
     *@return the number of events
     */
    public int size(){
        return size;
    }
    
    /** Returns the capacity
     *@return the capacity
     */
    public int capacity(){
        return length;
    }
    
    /** Returns an event added <code>k</code> ago.  
     @param k the event to get back, 0 being the last event added 
     */
    private BasicEvent getBackEvent(int k){
        if(k>=size) return null;
        int outInd = nextIn-k;
        if(outInd>=0) return array[outInd];
        outInd=length+outInd;
        return array[outInd];
    }
    
    private Iterator it;
    
    private final class Itr implements Iterator {
        int cursor = 0;
        
        public final boolean hasNext() {
            return cursor < size-1;
        }
        
        public final BasicEvent next() {
            return getBackEvent(++cursor);
        }
        public void reset(){
            cursor=0;
        }
        /** Unsupported operation */
        public void remove(){
            throw new UnsupportedOperationException();
        }
        
        public String toString(){
            return "EventBuffer cursor="+cursor+" for EventBuffer with size="+size;
        }
    }
    
    /** Returns the iterator over events. This iterator starts with the most recently added event and ends with the first event added or with the
     *capacity event if more have been added than the buffer's capacity.
     @return an iterator that can iterate over past events. Starts with most recently added event.
     */
    public final Iterator<BasicEvent> iterator(){
        if (itr==null){
            itr = new Itr();
        }else{
            itr.reset();
        }
        return itr;
    }
}