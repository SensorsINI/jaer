package net.sf.jaer.eventprocessing.tracking;

import java.util.Iterator;

import net.sf.jaer.event.BasicEvent;


/** A circular buffer of events with methods for adding new events and getting
 back old ones in the order of addition, last in is first out.
 *@author tobi delbruck
 */
final public class LIFOEventBuffer <E extends BasicEvent> implements Iterable<E> {
    private int length;
    private E[] array;
    boolean used[];
    private int nextIn = 0; // points to next location to add event
    private int size = 0;
    private Itr itr = null;
    Object elementObject;
    /**
     * Make a new instance of LIFOPolarityEventBuffer
     * 
     * @param array the array of objects holding the events
     */
    public LIFOEventBuffer(E[] array){
        if(array==null) throw new RuntimeException("null array passed to constructor");
        length=array.length;
        this.array=array;
        used=new boolean[length];
    }
    
    public String toString(){
        return "LIFOEventBuffer length="+length+" nextIn="+nextIn+" size="+size;
    }
    
    /** Adds an event to the end of the list. The iterator will iterate over the list, starting with this most-recently addeed event.
     *@param e the event
     */
    final public void add(E e){
        // debug
//        int lastIn=nextIn-1;
//        if(lastIn<0) lastIn=length-1;
//        OrientationEvent oldEvent=array[lastIn];
//        if(oldEvent!=null){
//            int told=array[lastIn].timestamp;
//            int dt=e.timestamp-told;
//            if(dt<0){
//                System.err.println("adding older event, dt="+dt);
//            }
//            if(oldEvent.serial>=e.serial){
//                System.err.println("previous serial="+oldEvent.serial+" this serial="+e.serial);
//            }
//        }
        size++;
//        array[nextIn]=e; // can't just copy reference because the event object could be used by someone else in a new packet
        array[nextIn].copyFrom(e); // copy fields to this array event object
        used[nextIn]=false;
        nextIn++;
        if(nextIn>=length) nextIn=0;
        if(size>=length) size=length;
//        System.out.println("added "+e+" to "+this);
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
    private E getBackEvent(int k){
        if(k>size) return null;
        int outInd = nextIn-k;
        if(outInd>=0) return array[outInd]; // event is before this location in array
        outInd=length+outInd; // array is after this location in array, wraps around to here
        return array[outInd];
    }
    
    private Iterator it;
    int cursor = 0;
    
    private final class Itr implements Iterator<E> {
        
        Itr(){
            cursor=0;
        }
        
        public final boolean hasNext() {
            return cursor < size;
        }
        
        public final E next() {
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
    final public Iterator<E> iterator(){
        if (itr==null){
            itr = new Itr();
        }else{
            itr.reset();
        }
        return itr;
    }
    
//    public static void main(String[] args){
//        OrientationEvent[] a=new OrientationEvent[3];
//        for(OrientationEvent e:a) e=new OrientationEvent();
//        LIFOEventBuffer<OrientationEvent> b=new LIFOEventBuffer(a);
//        for(int i=0;i<10;i++){
//            OrientationEvent e=new OrientationEvent();
//            e.timestamp=i;
//            for(OrientationEvent old:b){
//                System.out.println("had old "+old);
//            }
//            b.add(e);
//            System.out.println("added new "+e+"\n");
//        }
//        System.out.println("****");
//        for(OrientationEvent e:b){
//            System.out.println("got back "+e);
//        }
//    }
}