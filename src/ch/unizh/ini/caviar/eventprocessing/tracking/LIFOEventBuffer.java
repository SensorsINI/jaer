package ch.unizh.ini.caviar.eventprocessing.tracking;

import ch.unizh.ini.caviar.event.BasicEvent;
import java.util.Iterator;


/** A buffer of events with methods for adding new events and getting back old ones in the order of addition, last in is first out
 */
public class LIFOEventBuffer implements Iterable<BasicEvent> {
    private int length = 100;
    private BasicEvent[] array;
    private int nextIn = 0;
    private int size = 0;
    private Itr itr = null;
    
    public LIFOEventBuffer(int length){
        this.length=length;
        array = new BasicEvent[length];
    }
    public void add(BasicEvent e){
        size++;
        array[nextIn]=e;
        nextIn++;
        if(nextIn>=length) nextIn=0;
        if(size>=length) size=length;
    }
    public void clear(){
        nextIn=0;
        size=0;
    }
    /** Returns an event added <code>k</code> ago.  
     @param k the event to get back, 0 being the last event added 
     */
    private BasicEvent getBackEvent(int k){
        if(k>=size) return null;
        int outInd = nextIn-k;
        if(outInd>=0) return array[outInd];
        outInd=length-outInd;
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
    
    /** Returns after initializng the iterator over input events
     @return an iterator that can iterate over past events
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