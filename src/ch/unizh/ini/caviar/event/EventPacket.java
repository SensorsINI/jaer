 /** EventPacket.java
 *
 * Created on October 29, 2005, 10:18 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.event;

import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.eventprocessing.*;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * A packet of events that is used for rendering and event processing.
 For efficiency, these packets are designed to be re-used;
 they should be allocated rarely and allowed to grow in size. If a packet capacity needs to be
 increased a substantial peformance hit will occur e.g. 1 ms per resizing or initial allocation.
 <p>
 The EventPacket is prefilled with Events that have default values. One constructor lets
 you fill the EventPacket with a subclass of BasicEvent. This prefilling avoids
 the overhead of object creation. It also allows easy access to all information contained in the
 event and it allows storing arbitrary event information, including
 extended type information, in EventPackets.
 <p>
 However, this reuse of existing objects means that you need to take particular precautions. The events that are stored
 in a packet are references to objects. Therefore you can assign an event to a different packet
 but this event will still be referenced in the original packet
 and can change.
 <p>
 Generally in event processing, you will iterate over an input packet to produce an output packet.
 You iterate over an exsiting EventPacket that has input data using the iterator().
 This lets you access the events in the input packet.
 <p>
 When you want to write these events to an existing output packet,
 then you need to use the target event's copyFrom(Event e) method
 that copies all the fields in the
 source packet to the target packet. This lets you copy data such as
 timestamp, x,y location to a target event. You can then fill in the target event's extended
 type infomation.
 <p>
 When you iterate over an input packet to write to a target packet,
 you obtain the target event to write your results to by using the target packet's
 output enumeration by using the outputIterator() method. This enumeration has a method nextOutput() that returns the
 next output event to write to. This nextOutput() method also expands the packet if they current capacity needs to be enlarged.
 The iterator is initialized by the call to outputIterator().
 <p>
 The amount of time iterating over events can also be limited by using the time limiter.
 This static (class) method starts a timer when it is restarted and after timeout, no more events are returned
 from input iteration. These methods are used in FilterChain to limit processing time.
 
 * @author tobi
 */
public class EventPacket<E extends BasicEvent> implements /*EventPacketInterface<E>,*/ Cloneable, Iterable<E>{
    static Logger log=Logger.getLogger(EventPacket.class.getName());
    
    /** The time limiting Timer */
    private static TimeLimiter timeLimitTimer=new TimeLimiter();
    
    /** Resets the time limiter for input iteration. After the timer times out
     (time determined by timeLimitMs) input iterators will not return any more events.
     */
    static public void restartTimeLimiter(){
        timeLimitTimer.restart();
    }
    
    /** restart the time limiter with limit timeLimitMs
     @param timeLimitMs time in ms
     */
    public static void restartTimeLimiter(int timeLimitMs) {
        setTimeLimitMs(timeLimitMs);
        restartTimeLimiter();
    }

    /** Default capacity in events for new EventPackets */
    public final int DEFAULT_INITIAL_CAPACITY=4096;
    int capacity;
//    protected BasicEvent[] events;
//    protected ArrayList<E> eventList;
    private int numTypes=1;
    
    /** the number of events eventList actually contains (0 to size-1) */
    private int size=0;
    
    private Class eventClass=null;
    private Constructor eventConstructor=null;
    private E eventPrototype;
    
    private transient E[] elementData;
    
    public EventPacket(){
        this(BasicEvent.class);
    }
    
    public EventPacket(Class eventClass){
        if(!BasicEvent.class.isAssignableFrom(eventClass)){
            throw new Error("making EventPacket that holds "+eventClass+" but these are not assignable from BasicEvent");
        }
        this.eventClass=eventClass;
        try{
            eventConstructor=eventClass.getConstructor();
        }catch(NoSuchMethodException e){
            log.warning("cannot get constructor for constructing Events for building EventPacket: "+e.getCause());
            e.printStackTrace();
        }
        initializeEvents();
    }
    
    void initializeEvents(){
//        eventList=new ArrayList<E>(DEFAULT_INITIAL_CAPACITY);
//        elementData = (E[])new BasicEvent[DEFAULT_INITIAL_CAPACITY];
        elementData=(E[])Array.newInstance(eventClass,DEFAULT_INITIAL_CAPACITY);
        fillWithDefaultEvents(0,DEFAULT_INITIAL_CAPACITY);
        size=0;
        capacity=DEFAULT_INITIAL_CAPACITY;
    }
    
    void fillWithDefaultEvents(int startIndex, int endIndex){
        try{
            for(int i=startIndex;i<endIndex;i++){
                E e=(E)eventConstructor.newInstance();
//                eventList.add(e);
                elementData[i]=e;
                eventPrototype=e;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        
    }
    
    public int getDurationUs() {
        if(size<2) return 0; else return getLastTimestamp()-getFirstTimestamp();
    }
    
    public String getDescription() {
        return "";
    }
    
    
    public void clear() {
        size=0; // we don't clear list, because that nulls all the events
    }
    
    protected void setSize(int n) {
        size=n;
//        eventList.
//        this.numEvents=n;
    }
    
    /** @return event rate for this packet in Hz. If packet duration is zero, rate returned is zero. */
    public float getEventRateHz(){
        if(getDurationUs()==0) return 0;
        return (float)getSize()/getDurationUs()*1e6f;
    }
    
//    public void copyTo(EventPacket packet) {
//    }
    
    public E getFirstEvent(){
        if(size==0) return null;
        return elementData[0];
//        return eventList.get(0);
    }
    
    public E getLastEvent(){
        if(size==0) return null;
        return elementData[size-1];
//        return eventList.get(size-1);
    }
    
    public int getFirstTimestamp() {
//        if(events==null) return 0; else return events[0].timestamp;
        return elementData[0].timestamp;
//        return eventList.get(0).timestamp;
    }
    
    /** @return last timestamp in packet. 
     If packet is empty, returns zero - which could be important if this time is used for e.g. filtering operations!
     */
    public int getLastTimestamp() {
        if(size==0) return 0;
//        if(events==null) return 0; else return events[numEvents-1].timestamp;
        return elementData[size-1].timestamp;
//        return eventList.get(size-1).timestamp;
    }
    
    public void render(AEChip chip) {
    }
    
    public void display(AEChip chip) {
    }
    
    
    final public E getEvent(int k){
        if(k>=size) throw new ArrayIndexOutOfBoundsException();
        return elementData[k];
//        return eventList.get(k);
    }
    
    InItr inputIterator=null;
    
    /** Returns after initializng the iterator over input events 
     @return an iterator that can iterate over events 
     */
    final public Iterator<E> inputIterator(){
        if(inputIterator==null){
            inputIterator=new InItr();
        }else{
            inputIterator.reset();
        }
        return inputIterator;
    }
    
    OutItr outputIterator=null;
    static int nextSerial=0;
    
    final public OutputEventIterator<E> outputIterator(){
        if(outputIterator==null){
            outputIterator=new OutItr();
        }else{
            outputIterator.reset();
        }
        return outputIterator;
    }
    
    final private class OutItr implements OutputEventIterator {
        OutItr(){
            size=0; // reset size because we are starting off output packet
        }
        /** obtains the next output event. Increments the size of the packet */
        final public E nextOutput() {
            E next;
            if(size>=capacity)
                enlargeCapacity();
//            try {
//                next = eventList.get(cursor);
            next=elementData[size];
//            next.serial=nextSerial++;
//            } catch(IndexOutOfBoundsException e) {
//                enlargeCapacity();
//                next=eventList.get(cursor);
//            }
            size++;
            return next;
        }
        
        void reset(){
            size=0;
        }
        
        public String toString(){
            return "OutputEventIterator for packet with size="+size;
        }
    }
    
    final private class InItr implements Iterator<E> {
        int cursor;
        
        public InItr(){
            reset();
        }
        
        final public boolean hasNext() {
            return cursor < size && !timeLimitTimer.isTimedOut();
        }
        
        final public E next() {
//            try { // removed array check because we should always be using hasNext before calling this
//                E next = eventList.get(cursor);
            E next=elementData[cursor++];
            return next;
//            } catch(IndexOutOfBoundsException e) {
//                return null;
//            }
        }
        public void reset(){
            cursor=0;
        }
        
        public void remove(){
            for (int ctr=cursor;ctr<size;ctr++)
            {
              elementData[cursor-1]=elementData[cursor];  
            }
            //go back as we removed a packet
            cursor--;
            size--;
            //throw new UnsupportedOperationException();
        }
        
        public String toString(){
            return "InputEventIterator cursor="+cursor+" for packet with size="+size;
        }
    }
    
    /** Enlarges capacity by some factor, then copies all event references to the new packet */
    private void enlargeCapacity() {
        int ncapacity=capacity*2; // (capacity*3)/2+1;
        Object oldData[] = elementData;
        elementData = (E[])new BasicEvent[ncapacity];
        System.arraycopy(oldData, 0, elementData, 0, size);
        // capacity still is old capacity and we have already filled it to there with new events, now fill
        // in up to new capacity with new events
        fillWithDefaultEvents(capacity,ncapacity);
        capacity=ncapacity;
        
    }
    
//    public static void main(String[] args){
//        EventPacket p=new EventPacket();
//        p.test();
//    }
//    
    /**
     0.32913625s for 300 n allocations, 1097.1208 us/packet
     0.3350817s for 300 n allocations, 1116.939 us/packet
     0.3231394s for 300 n allocations, 1077.1313 us/packet
     0.32404426s for 300 n allocations, 1080.1475 us/packet
     0.3472975s for 300 n allocations, 1157.6583 us/packet
     0.33720487s for 300 n allocations, 1124.0162 us/packet
     */
//    void test(){
//        int nreps=5;
//        int size=30000;
//        long stime, etime;
//        EventPacket<BasicEvent> p,pout;
//        OutputEventIterator outItr;
//        Iterator<BasicEvent> inItr;
//        
//        System.out.println("make new packets");
//        for(int k=0;k<nreps;k++){
//            stime=System.nanoTime();
//            for(int i=0;i<nreps;i++){
//                p=new EventPacket();
//            }
//            etime=System.nanoTime();
//            
//            float timeSec=(etime-stime)/1e9f;
//            
//            System.out.println(timeSec+ "s"+" for "+nreps+" n allocations, "+1e6f*timeSec/nreps+" us/packet ");
//            System.out.flush();
//            try{
//                Thread.currentThread().sleep(10);
//            }catch(Exception e){}
//        }
//        
//        System.out.println("make a new packet and fill with events");
//        p=new EventPacket<BasicEvent>();
//        for(int k=0;k<nreps;k++){
//            stime=System.nanoTime();
//            outItr=p.outputIterator();
//            for(int i=0;i<size;i++){
//                BasicEvent e=outItr.nextOutput();
//                e.timestamp=i;
//                e.x=((short)i);
//                e.y=(e.x);
//            }
//            etime=System.nanoTime();
//            
//            float timeSec=(etime-stime)/1e9f;
//            
//            System.out.println(timeSec+ "s"+" for "+size+" fill, "+1e6f*timeSec/size+" us/event ");
//            System.out.flush();
//            try{
//                Thread.currentThread().sleep(10);
//            }catch(Exception e){}
//        }
//        
//        
//        System.out.println("iterate over packet, changing all values");
////        p=new EventPacket();
//        pout=new EventPacket<BasicEvent>();
//        
//        for(int k=0;k<nreps;k++){
//            stime=System.nanoTime();
//            inItr=p.inputIterator();
//            outItr=pout.outputIterator();
//            for(BasicEvent ein:p){
//                
////                while(inItr.hasNext()){
////                BasicEvent ein=inItr.next();
//                BasicEvent eout=outItr.nextOutput();
//                eout.copyFrom(ein);
//            }
//            etime=System.nanoTime();
//            
//            float timeSec=(etime-stime)/1e9f;
//            
//            System.out.println(timeSec+ "s"+" for iteration over packet with size="+p.getSize()+", "+timeSec/p.getSize()+" s per event");
//            System.out.flush();
//            try{
//                Thread.currentThread().sleep(10);
//            }catch(Exception e){}
//        }
//        
//        System.out.println("\nmake packet with OrientationEvent and assign polarity and orientation");
//        pout=new EventPacket(OrientationEvent.class);
//        OrientationEvent ori=null;
//        for(int k=0;k<nreps;k++){
//            stime=System.nanoTime();
//            outItr=pout.outputIterator();
//            for(int i=0;i<size;i++){
//                ori=(OrientationEvent)outItr.nextOutput();
//                ((PolarityEvent)ori).type=10;
//                ori.timestamp=i;
//                ori.orientation=(byte)(100);
////                ori.polarity=(byte)(20);
//            }
//            etime=System.nanoTime();
//            float timeSec=(etime-stime)/1e9f;
//            
//            System.out.println(timeSec+ "s"+" for iteration over packet with size="+p.getSize()+", "+timeSec/p.getSize()+" s per event");
//            System.out.flush();
//            try{
//                Thread.currentThread().sleep(10);
//            }catch(Exception e){}
//        }
//        System.out.println("ori event ="+pout.getEvent(0)+" with type="+ori.getType());
//        System.out.println(pout.toString());
//        
//    }
//    
    final public int getSize() {
        return size;
    }
    
    public boolean isEmpty(){
        return size==0? true: false;
    }
    
    public String toString(){
        int size=getSize();
        String s="EventPacket holding "+getEventClass().getSimpleName()+" with size="+size+" capacity="+capacity;
        return s;
    }
        
    final public int getNumCellTypes() {
        return eventPrototype.getNumCellTypes();
    }
    
    final public E getEventPrototype(){
        return eventPrototype;
    }
    
    /** Initializes and returns the iterator */
    final public Iterator<E> iterator() {
        return inputIterator();
    }
    
    final public Class getEventClass() {
        return eventClass;
    }

    /** Gets the class time limit for iteration in ms
     */
    final public static int getTimeLimitMs() {
        return timeLimitTimer.getTimeLimitMs();
    }

    /** Sets the class time limit for filtering a packet through the filter chain in ms.
     @param timeLimitMs the time limit in ms 
     @see #restartTimeLimiter
     */
    final public static void setTimeLimitMs(int timeLimitMs) {
        timeLimitTimer.setTimeLimitMs(timeLimitMs);
    }
    
    final public static void setTimeLimitEnabled(boolean yes){
        timeLimitTimer.setEnabled(yes);
    }
    
    /** Returns status of time limiting
     @return true if timelimiting is enabled
     */
    final public static boolean isTimeLimitEnabled(){
        return timeLimitTimer.isEnabled();
    }

    /** Returns true if timeLimitTimer is timed out and timeLimitEnabled */
    final public static boolean isTimedOut() {
        return timeLimitTimer.isTimedOut();
    }

}

